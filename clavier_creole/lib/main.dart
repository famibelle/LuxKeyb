import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;

void main() {
  runApp(const CreoleKeyboardApp());
}

class CreoleKeyboardApp extends StatelessWidget {
  const CreoleKeyboardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Clavier Créole',
      home: Scaffold(
        appBar: AppBar(title: const Text('Klavié Kreyol')),
        body: const KeyboardDemo(),
      ),
    );
  }
}

class KeyboardDemo extends StatefulWidget {
  const KeyboardDemo({super.key});
  @override
  State<KeyboardDemo> createState() => _KeyboardDemoState();
}

class _KeyboardDemoState extends State<KeyboardDemo> {
  final TextEditingController _controller = TextEditingController();
  List<String> dictionary = [];
  List<String> suggestions = [];
  bool showKeyboard = true;

  // Layout du clavier créole
  final List<List<String>> keyboardLayout = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
    ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
    ['z', 'x', 'c', 'v', 'b', 'n', 'm'],
  ];

  // Caractères spéciaux créoles
  final List<String> creoleChars = ['ô', 'é', 'è', 'ò', '\''];

  @override
  void initState() {
    super.initState();
    loadDictionary();
    _controller.addListener(updateSuggestions);
  }

  Future<void> loadDictionary() async {
    final jsonStr = await rootBundle.loadString('assets/creole_dict.json');
    final List<dynamic> data = json.decode(jsonStr);
    setState(() {
      dictionary = data.map((e) => e[0].toString()).toList();
    });
  }

  void updateSuggestions() {
    final text = _controller.text.trim().split(' ').last.toLowerCase();
    if (text.isEmpty) {
      setState(() => suggestions = []);
      return;
    }
    setState(() {
      suggestions = dictionary
          .where((word) => word.startsWith(text))
          .take(3)
          .toList();
    });
  }

  void insertWord(String word) {
    final parts = _controller.text.split(' ');
    parts.removeLast();
    parts.add(word);
    _controller.text = parts.join(' ') + ' ';
    _controller.selection = TextSelection.fromPosition(
      TextPosition(offset: _controller.text.length),
    );
    updateSuggestions();
  }

  void insertCharacter(String char) {
    final cursorPos = _controller.selection.start >= 0 ? _controller.selection.start : _controller.text.length;
    final text = _controller.text;
    final newText = text.substring(0, cursorPos) + char + text.substring(cursorPos);
    _controller.text = newText;
    _controller.selection = TextSelection.fromPosition(
      TextPosition(offset: cursorPos + char.length),
    );
    updateSuggestions();
  }

  void deleteLastCharacter() {
    if (_controller.text.isNotEmpty) {
      final cursorPos = _controller.selection.start;
      if (cursorPos > 0) {
        final text = _controller.text;
        final newText = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
        _controller.text = newText;
        _controller.selection = TextSelection.fromPosition(
          TextPosition(offset: cursorPos - 1),
        );
        updateSuggestions();
      }
    }
  }

  Widget buildKeyboardButton(String char, {bool isSpecial = false, double? width}) {
    return Container(
      width: width ?? 35,
      height: 40,
      margin: const EdgeInsets.all(2),
      child: ElevatedButton(
        onPressed: () {
          if (char == '⌫') {
            deleteLastCharacter();
          } else if (char == ' ') {
            insertCharacter(' ');
          } else {
            insertCharacter(char);
          }
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: isSpecial ? Colors.orange.shade300 : Colors.blue.shade100,
          padding: EdgeInsets.zero,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
        ),
        child: Text(
          char,
          style: TextStyle(
            fontSize: char == '⌫' ? 16 : 14,
            fontWeight: isSpecial ? FontWeight.bold : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  Widget buildKeyboard() {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.grey.shade200,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        children: [
          // Rangée de caractères spéciaux créoles
          Wrap(
            alignment: WrapAlignment.center,
            children: creoleChars.map((char) => buildKeyboardButton(char, isSpecial: true)).toList(),
          ),
          const SizedBox(height: 8),
          // Rangées du clavier principal
          ...keyboardLayout.map((row) => Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: row.map((char) => buildKeyboardButton(char)).toList(),
          )),
          // Rangée avec espace et backspace
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              buildKeyboardButton('⌫'),
              buildKeyboardButton(' ', width: 150),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Zone de saisie
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: TextField(
            controller: _controller,
            decoration: InputDecoration(
              border: const OutlineInputBorder(),
              hintText: 'Maké an kreyol...',
              suffixIcon: IconButton(
                icon: Icon(showKeyboard ? Icons.keyboard_hide : Icons.keyboard),
                onPressed: () {
                  setState(() {
                    showKeyboard = !showKeyboard;
                  });
                },
              ),
            ),
          ),
        ),
        // Suggestions
        if (suggestions.isNotEmpty)
          Container(
            height: 50,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              itemCount: suggestions.length,
              itemBuilder: (context, index) {
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ElevatedButton(
                    onPressed: () => insertWord(suggestions[index]),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green.shade100,
                    ),
                    child: Text(suggestions[index]),
                  ),
                );
              },
            ),
          ),
        const SizedBox(height: 16),
        // Clavier virtuel
        if (showKeyboard)
          Expanded(
            child: SingleChildScrollView(
              child: buildKeyboard(),
            ),
          ),
      ],
    );
  }
}
