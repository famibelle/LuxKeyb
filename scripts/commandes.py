#!/usr/bin/env python3
"""Prépare les commandes de la boutique en ligne (BOUTIQUE-CARTES.md).

Un jeu commandé sur le site (la boîte de départ, une extension) se paie par un
lien de paiement Stripe, puis il est imprimé à la demande et envoyé par
l'imprimeur directement chez le client. Ce script fait le lien entre les deux,
chez le propriétaire :

1. il lit les paiements Stripe terminés (les sessions Checkout payées, sans
   les remboursées) ;
2. il donne à chaque jeu une carte code signée, prise dans une réserve de
   impression/codes/ faite par scripts/codes_cartes.py ; le nom de la réserve
   est la métadonnée `codes` du produit Stripe (`B1-boutique`, `E1-boutique`) ;
3. il écrit, par commande, la fiche à recopier chez l'imprimeur (l'adresse du
   client) et l'e-mail qui porte le ou les codes QR ;
4. sur demande, il envoie ces e-mails.

    pip install segno
    export STRIPE_CLE=rk_live_...   # clé restreinte, en lecture seule
    python3 scripts/commandes.py preparer
    python3 scripts/commandes.py envoyer   # SMTP_HOTE, SMTP_UTILISATEUR, SMTP_MOT_DE_PASSE

Tout ce qu'il écrit va dans impression/commandes/ (impression/commandes-test/
avec une clé de test), hors dépôt : noms, adresses et codes signés n'ont rien
à faire dans un dépôt public. Le registre (registre.csv) garde chaque commande
préparée et ses codes : une commande ne se prépare qu'une fois, un code ne se
donne qu'une fois. La colonne `imprimeur` est au propriétaire : il y note le
numéro de la commande passée chez l'imprimeur.
"""
import argparse
import csv
import io
import json
import os
import re
import smtplib
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime
from email import policy
from email.message import EmailMessage
from email.parser import BytesParser
from zoneinfo import ZoneInfo

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
IMPRESSION = os.path.join(RACINE, 'impression')
CODES = os.path.join(IMPRESSION, 'codes')
API = 'https://api.stripe.com/v1'
# Version figée : la forme des réponses ne suit pas les mises à jour du compte.
# Depuis 2025-03-31, l'adresse de livraison d'une session est passée dans
# collected_information ; livraison() lit les deux.
VERSION_STRIPE = '2024-06-20'
FUSEAU = ZoneInfo('Europe/Luxembourg')
PAYS = {'LU': 'Luxembourg', 'BE': 'Belgique', 'FR': 'France', 'DE': 'Allemagne'}
COLONNES = ['numero', 'session', 'date', 'nom', 'email', 'pays', 'quantite',
            'montant', 'codes', 'mail_envoye', 'imprimeur']
SITE = 'https://famibelle.github.io/LuxKeyb'


# ------------------------------------------------------------------ Stripe

def stripe(cle, chemin, params=()):
    url = f'{API}/{chemin}'
    if params:
        url += '?' + urllib.parse.urlencode(params)
    requete = urllib.request.Request(url, headers={
        'Authorization': f'Bearer {cle}', 'Stripe-Version': VERSION_STRIPE})
    try:
        with urllib.request.urlopen(requete, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        try:
            message = json.load(e)['error']['message']
        except (ValueError, KeyError):
            message = e.reason
        sys.exit(f'Stripe refuse {chemin} ({e.code}) : {message}')


def liste(cle, chemin, params=()):
    """Tous les éléments d'une liste Stripe, page après page."""
    apres = None
    while True:
        suite = [('starting_after', apres)] if apres else []
        page = stripe(cle, chemin, [*params, ('limit', 100), *suite])
        yield from page['data']
        if not page.get('has_more') or not page['data']:
            return
        apres = page['data'][-1]['id']


def rembourse(session):
    paiement = session.get('payment_intent')
    charge = paiement.get('latest_charge') if isinstance(paiement, dict) else None
    return isinstance(charge, dict) and charge.get('refunded', False)


def sessions_payees(cle):
    """Les paiements terminés et non remboursés, du plus ancien au plus récent."""
    sessions = liste(cle, 'checkout/sessions', [
        ('status', 'complete'), ('expand[]', 'data.payment_intent.latest_charge')])
    payees = [s for s in sessions if s.get('payment_status') == 'paid' and not rembourse(s)]
    return sorted(payees, key=lambda s: s['created'])


def articles(cle, session):
    """(produit, quantité, réserve de codes) pour chaque article de cartes de la session."""
    lignes = []
    for article in liste(cle, f'checkout/sessions/{session}/line_items',
                         [('expand[]', 'data.price.product')]):
        produit = article['price']['product']
        nom = article.get('description') or produit.get('name', '?')
        reserve = (produit.get('metadata') or {}).get('codes')
        if not reserve:
            print(f'  {session} : « {nom} » n\'a pas de métadonnée « codes », ignoré')
            continue
        if not re.fullmatch(r'[A-Za-z0-9_-]+', reserve):
            sys.exit(f'métadonnée « codes » invalide pour « {nom} » : {reserve!r}')
        lignes.append((nom, article['quantity'], reserve))
    return lignes


def livraison(session):
    collecte = session.get('collected_information') or {}
    infos = collecte.get('shipping_details') or session.get('shipping_details') or {}
    return infos.get('name') or '', infos.get('address') or {}


# ---------------------------------------------------- registre et réserves

def dossier_base(cle):
    if not cle:
        sys.exit('STRIPE_CLE absente : la clé dit aussi s\'il s\'agit des commandes réelles ou de test')
    return os.path.join(IMPRESSION, 'commandes-test' if '_test_' in cle else 'commandes')


def lire_registre(chemin):
    if not os.path.exists(chemin):
        return []
    with open(chemin, newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def ecrire_registre(chemin, lignes):
    """Réécrit le registre d'un coup, en gardant les colonnes ajoutées à la main."""
    champs = COLONNES + [k for k in (lignes[0] if lignes else {}) if k not in COLONNES]
    provisoire = chemin + '.tmp'
    with open(provisoire, 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=champs)
        w.writeheader()
        w.writerows(lignes)
    os.replace(provisoire, chemin)


def reserve_libre(reserve, donnes):
    """Les codes de la réserve qui n'ont encore été donnés à personne, dans l'ordre du fichier."""
    chemin = os.path.join(CODES, reserve, 'codes.csv')
    if not os.path.exists(chemin):
        sys.exit(f'réserve de codes absente : {chemin}\n'
                 f'La signer : python3 scripts/codes_cartes.py @{reserve.split("-")[0]} -n 200 '
                 f'-o impression/codes/{reserve}')
    with open(chemin, newline='', encoding='utf-8') as f:
        return [(l['serie'], l['code']) for l in csv.DictReader(f) if l['serie'] not in donnes]


# ------------------------------------------------------- fiche et e-mail

def montant(session):
    return f"{session['amount_total'] / 100:.2f}".replace('.', ',') + ' ' + session['currency'].upper()


def fiche(numero, session, lignes):
    nom, adresse = livraison(session)
    client = session.get('customer_details') or {}
    date = datetime.fromtimestamp(session['created'], FUSEAU).strftime('%Y-%m-%d %H:%M')
    pays = adresse.get('country') or '?'
    ville = ' '.join(x for x in (adresse.get('postal_code'), adresse.get('city')) if x)
    adresse_lignes = [nom, adresse.get('line1'), adresse.get('line2'), ville, adresse.get('state'),
                      f'{PAYS.get(pays, pays).upper()} ({pays})']
    texte = [f'{numero} · payée le {date} · {montant(session)}']
    texte += [f'  {q} × {produit}' for produit, q, _ in lignes]
    for i, ligne in enumerate(x for x in adresse_lignes if x):
        texte.append(('  Livrer à : ' if i == 0 else '             ') + ligne)
    if pays not in PAYS:
        texte.append(f'  ⚠ {pays} n\'est pas un pays de livraison de la boutique : vérifier avant de commander')
    texte.append(f"  Téléphone : {client.get('phone') or '—'}")
    texte.append(f"  E-mail    : {client.get('email') or '—'}")
    texte.append("  N° chez l'imprimeur : à noter dans registre.csv, colonne « imprimeur »")
    return '\n'.join(texte) + '\n'


def courriel(numero, session, lignes, pngs, delai, expediteur):
    """L'e-mail des codes ; `pngs` donne, pour chaque code, son jeu et son image."""
    client = session.get('customer_details') or {}
    nom = client.get('name') or livraison(session)[0]
    n = len(pngs)
    produits = '\n'.join(f'  {q} × {produit}' for produit, q, _ in lignes)
    noms = ['code.png'] if n == 1 else [f'code-{i}.png' for i in range(1, n + 1)]
    if n == 1:
        codes = 'Le code joint (code.png) les ajoute à votre carnet :'
    else:
        codes = ('Chaque code joint ajoute à votre carnet les cartes d\'un jeu :\n' +
                 '\n'.join(f'  {f} : {produit}' for f, (produit, _) in zip(noms, pngs)) +
                 '\nPour chacun :')
    m = EmailMessage()
    if expediteur:
        m['From'] = expediteur
    m['To'] = client['email']
    m['Subject'] = f"Wuertduell {numero} : {'le code de vos cartes' if n == 1 else 'les codes de vos cartes'}"
    m.set_content(f"""Bonjour {nom},

Merci pour votre commande {numero} :
{produits}

Chaque jeu est imprimé à la demande, rien que pour vous, puis envoyé
directement par l'imprimeur à l'adresse donnée lors du paiement. Comptez
{delai}.

Vos cartes vous attendent aussi dans l'application Lëtzebuergesch Clavier.
{codes}
  1. Dans le carnet de l'application, touchez « 🎁 Recevoir ».
  2. Choisissez « 🃏 Le code d'une carte en papier ».
  3. Visez le code, affiché sur un ordinateur ou imprimé : un téléphone ne
     peut pas scanner son propre écran.
Un code ne sert qu'une fois par téléphone : gardez-le pour vous.

Vous pouvez renoncer à votre achat dans les 14 jours qui suivent la
réception : répondez simplement à cet e-mail. Une question, un colis qui
n'arrive pas ? Même adresse.

Bonne partie !
Lëtzebuergesch Clavier
{SITE}/regle-du-jeu.html
""")
    for f, (_, png) in zip(noms, pngs):
        m.add_attachment(png, maintype='image', subtype='png', filename=f)
    return m


def qr_png(texte):
    import segno
    tampon = io.BytesIO()
    segno.make(texte, error='m', micro=False).save(tampon, kind='png', scale=8, border=4, dark='#1B1610')
    return tampon.getvalue()


# ------------------------------------------------------------- commandes

def preparer(a):
    cle = os.environ.get('STRIPE_CLE', '')
    base = dossier_base(cle)
    os.makedirs(base, exist_ok=True)
    chemin = os.path.join(base, 'registre.csv')
    registre = lire_registre(chemin)
    faites = {l['session'] for l in registre}
    donnes = {c.rsplit('/', 1)[-1] for l in registre for c in l['codes'].split()}

    nouvelles = []
    for session in sessions_payees(cle):
        if session['id'] not in faites:
            lignes = articles(cle, session['id'])
            if lignes:
                nouvelles.append((session, lignes))
    if not nouvelles:
        print('Aucune nouvelle commande.')
        return

    # Toutes les réserves d'abord : faute de codes, rien n'est préparé.
    besoin = Counter()
    for _, lignes in nouvelles:
        for _, q, reserve in lignes:
            besoin[reserve] += q
    libres = {r: reserve_libre(r, donnes) for r in besoin}
    manque = [f'{r} : {besoin[r]} demandés, {len(libres[r])} libre(s)' for r in besoin if len(libres[r]) < besoin[r]]
    if manque:
        sys.exit('Pas assez de codes, rien n\'est préparé :\n  ' + '\n  '.join(manque) +
                 '\nEn signer d\'autres avec scripts/codes_cartes.py, dans une nouvelle réserve ou la même.')

    expediteur = os.environ.get('SMTP_EXPEDITEUR') or os.environ.get('SMTP_UTILISATEUR')
    fiches = []
    for session, lignes in nouvelles:
        numero = f'WD-{len(registre) + 1:04d}'
        dossier = os.path.join(base, numero)
        os.makedirs(dossier, exist_ok=True)
        donnes_ici, pngs = [], []
        for produit, q, reserve in lignes:
            for _ in range(q):
                serie, texte = libres[reserve].pop(0)
                png = qr_png(texte)
                with open(os.path.join(dossier, f'{serie}.png'), 'wb') as f:
                    f.write(png)
                donnes_ici.append(f'{reserve}/{serie}')
                pngs.append((produit, png))
        texte = fiche(numero, session, lignes)
        with open(os.path.join(dossier, 'fiche.txt'), 'w', encoding='utf-8') as f:
            f.write(texte)
        with open(os.path.join(dossier, 'mail.eml'), 'wb') as f:
            f.write(bytes(courriel(numero, session, lignes, pngs, a.delai, expediteur)))
        client = session.get('customer_details') or {}
        registre.append({
            'numero': numero, 'session': session['id'],
            'date': datetime.fromtimestamp(session['created'], FUSEAU).strftime('%Y-%m-%d %H:%M'),
            'nom': livraison(session)[0] or client.get('name', ''), 'email': client.get('email', ''),
            'pays': livraison(session)[1].get('country', ''), 'quantite': len(donnes_ici),
            'montant': montant(session), 'codes': ' '.join(donnes_ici), 'mail_envoye': '', 'imprimeur': ''})
        # Le registre est le point de non-retour : écrit après chaque commande,
        # un arrêt en route ne donne jamais deux fois un code ni une commande.
        ecrire_registre(chemin, registre)
        fiches.append(texte)

    resume = os.path.join(base, f"a_commander_{datetime.now(FUSEAU).strftime('%Y-%m-%d_%H%M')}.txt")
    with open(resume, 'w', encoding='utf-8') as f:
        f.write('\n'.join(fiches))
    print('\n'.join(fiches))
    print(f'{len(fiches)} commande(s) à passer chez l\'imprimeur : {resume}')
    print('E-mails prêts (mail.eml de chaque commande) : les relire, puis « commandes.py envoyer ».')


def envoyer(a):
    base = dossier_base(os.environ.get('STRIPE_CLE', ''))
    chemin = os.path.join(base, 'registre.csv')
    registre = lire_registre(chemin)
    attente = [l for l in registre if not l['mail_envoye']]
    if not attente:
        print('Aucun e-mail en attente.')
        return
    manque = [v for v in ('SMTP_HOTE', 'SMTP_UTILISATEUR', 'SMTP_MOT_DE_PASSE') if not os.environ.get(v)]
    if manque:
        sys.exit('Variables absentes : ' + ', '.join(manque))
    hote, port = os.environ['SMTP_HOTE'], int(os.environ.get('SMTP_PORT', '465'))
    expediteur = os.environ.get('SMTP_EXPEDITEUR') or os.environ['SMTP_UTILISATEUR']
    contexte = ssl.create_default_context()
    smtp = smtplib.SMTP_SSL(hote, port, context=contexte) if port == 465 else smtplib.SMTP(hote, port)
    with smtp:
        if port != 465:
            smtp.starttls(context=contexte)
        smtp.login(os.environ['SMTP_UTILISATEUR'], os.environ['SMTP_MOT_DE_PASSE'])
        for ligne in attente:
            with open(os.path.join(base, ligne['numero'], 'mail.eml'), 'rb') as f:
                message = BytesParser(policy=policy.default).parse(f)
            del message['From']
            message['From'] = expediteur
            smtp.send_message(message)
            ligne['mail_envoye'] = datetime.now(FUSEAU).strftime('%Y-%m-%d %H:%M')
            ecrire_registre(chemin, registre)
            print(f"  {ligne['numero']} → {ligne['email']}")
    print(f'{len(attente)} e-mail(s) envoyé(s).')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sous = p.add_subparsers(dest='action', required=True)
    prep = sous.add_parser('preparer', help='nouvelles commandes : codes, fiches pour l\'imprimeur, e-mails')
    prep.add_argument('--delai', default='deux à trois semaines', help='délai annoncé dans l\'e-mail')
    sous.add_parser('envoyer', help='envoie les e-mails préparés et pas encore partis')
    a = p.parse_args()
    {'preparer': preparer, 'envoyer': envoyer}[a.action](a)


if __name__ == '__main__':
    main()
