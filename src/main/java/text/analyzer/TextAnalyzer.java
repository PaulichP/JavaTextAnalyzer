package text.analyzer;

import com.google.gson.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TextAnalyzer {

    // Путь для вывода статистики по умолчанию
    private static final String DEFAULT_OUTPUT_PATH = "full_statistics.txt";
    // Запрещенные символы
    private static final List<Character> FORBIDDEN_CHARACTERS = Arrays.asList(',', ';', ':', '.', '!', '?', '/', '-');

    // Главный метод
    public static void main(String[] args) {
        try {
            // Настройка уровня логгирования
            org.apache.logging.log4j.core.config.Configurator.setLevel("org.apache.logging.log4j", org.apache.logging.log4j.Level.ERROR);

            Scanner scanner = new Scanner(System.in);
            System.out.print("Введите путь к файлу для анализа: ");
            String filePath = scanner.nextLine();

            // Проверка наличия файла
            if (isFileExists(filePath)) {
                System.out.println("Файл не найден. Проверьте путь и повторите попытку.");
                return;
            }

            // Если файл в формате doc/docx, конвертируем в txt
            if (filePath.endsWith(".doc") || filePath.endsWith(".docx")) {
                System.out.println("Обнаружен файл формата doc/docx. Конвертация в txt...");

                filePath = convertDocToTxt(filePath);

                System.out.println("Файл конвертирован в txt. Продолжаем анализ с новым файлом: " + filePath);
            }

            System.out.print("Введите путь к файлу словаря (JSON): ");
            String dictionaryPath = scanner.nextLine();

            // Проверка наличия файла словаря
            if (isFileExists(dictionaryPath)) {
                System.out.println("Файл не найден. Проверьте путь и повторите попытку.");
                return;
            }

            // Создание экземпляра анализатора
            TextAnalyzer analyzer = new TextAnalyzer(dictionaryPath);
            boolean exit = false;

            // Основной цикл программы
            while (!exit) {
                System.out.println("\nВыберите действие:");
                System.out.println("1. Определение темы текста");
                System.out.println("2. Отображение самых часто используемых слов");
                System.out.println("3. Запись полной статистики в файл");
                System.out.println("4. Выход");
                System.out.print("Ваш выбор: ");

                String choiceInput = scanner.nextLine();

                try {
                    int choice = Integer.parseInt(choiceInput);
                    switch (choice) {
                        case 1:
                            // Анализ текста и определение темы
                            String theme = analyzer.analyzeFile(filePath);
                            System.out.println("Тема текста: " + theme);
                            break;

                        case 2:
                            // Отображение самых часто используемых слов
                            int topWordsCount;
                            try {
                                System.out.println("Введите количество слов для отображения: ");
                                topWordsCount = Integer.parseInt(scanner.nextLine());

                                if (topWordsCount <= 0) {
                                    System.out.println("Ошибка: введите положительное число больше 0.");
                                    continue;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Ошибка: введите корректное число.");
                                continue;
                            }

                            System.out.println("Показать гистограмму? (yes/no): ");
                            String showHistogramInput = scanner.nextLine().toLowerCase();

                            boolean showHistogram;

                            if (showHistogramInput.equals("yes") || showHistogramInput.equals("y")) {
                                showHistogram = true;
                            } else if (showHistogramInput.equals("no") || showHistogramInput.equals("n")) {
                                showHistogram = false;
                            } else {
                                System.out.println("Некорректный ввод. Используйте yes/no, y/n.");
                                continue;
                            }

                            analyzer.displayTopWords(filePath, topWordsCount, showHistogram);
                            break;

                        case 3:
                            // Запись полной статистики в файл
                            System.out.print("Введите путь к файлу для записи статистики (по умолчанию - рядом с программой): ");
                            String outputFilePath = scanner.nextLine();
                            analyzer.writeFullStatisticsToFile(filePath, outputFilePath);
                            break;

                        case 4:
                            exit = true;
                            System.out.println("Программа завершена.");
                            break;

                        default:
                            System.out.println("Некорректный выбор. Попробуйте еще раз.");
                            break;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Ошибка: введите число от 1 до 4.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Конструктор класса анализатора
    private Map<String, String[]> dictionary;

    public TextAnalyzer(String dictionaryPath) throws IOException {
        this.dictionary = loadDictionary(dictionaryPath);
    }

    // Метод для отображения гистограммы
    public void displayHistogram(Map<String, Integer> wordOccurrences, int topWordsCount) {
        System.out.println("\nГистограмма:");

        List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(wordOccurrences.entrySet());
        sortedList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        int maxWordLength = 20;

        for (int i = 0; i < Math.min(topWordsCount, sortedList.size()); i++) {
            Map.Entry<String, Integer> entry = sortedList.get(i);

            String word = entry.getKey();
            int occurrences = entry.getValue();

            if (word.length() > maxWordLength) {
                word = word.substring(0, maxWordLength);
            }

            System.out.printf("%-" + (maxWordLength + 5) + "s |", word);
            for (int j = 0; j < occurrences; j++) {
                System.out.print("*");
            }
            System.out.println();
        }
    }

    // Загрузка словаря из файла JSON
    private Map<String, String[]> loadDictionary(String filePath) throws IOException {
        Gson gson = new Gson();
        JsonArray jsonArray = JsonParser.parseReader(new FileReader(filePath)).getAsJsonArray();

        Map<String, String[]> dictionary = new HashMap<>();

        for (JsonElement element : jsonArray) {
            JsonObject entry = element.getAsJsonObject();
            String theme = entry.get("theme").getAsString();
            String[] words = gson.fromJson(entry.get("words"), String[].class);
            dictionary.put(theme, words);
        }

        return dictionary;
    }

    // Анализ файла и определение темы
    public String analyzeFile(String filePath) throws IOException {
        Scanner fileScanner = new Scanner(new File(filePath));
        Map<String, Integer> themeOccurrences = new HashMap<>();

        while (fileScanner.hasNext()) {
            String word = fileScanner.next().toLowerCase();

            for (Map.Entry<String, String[]> entry : dictionary.entrySet()) {
                String theme = entry.getKey();
                String[] words = entry.getValue();

                for (String dictWord : words) {
                    if (word.contains(dictWord.toLowerCase())) {
                        themeOccurrences.put(theme, themeOccurrences.getOrDefault(theme, 0) + 1);
                    }
                }
            }
        }

        return findMaxOccurrenceTheme(themeOccurrences);
    }

    // Поиск темы с максимальным количеством вхождений
    private String findMaxOccurrenceTheme(Map<String, Integer> themeOccurrences) {
        String maxTheme = null;
        int maxOccurrences = 0;

        for (Map.Entry<String, Integer> entry : themeOccurrences.entrySet()) {
            if (entry.getValue() > maxOccurrences) {
                maxOccurrences = entry.getValue();
                maxTheme = entry.getKey();
            }
        }

        return maxTheme;
    }

    // Отображение часто используемых слов
    public void displayTopWords(String filePath, int topWordsCount, boolean showHistogram) throws IOException {
        Scanner fileScanner = new Scanner(new File(filePath));
        Map<String, Integer> wordOccurrences = new HashMap<>();

        while (fileScanner.hasNext()) {
            String word = fileScanner.next().toLowerCase();

            word = removeForbiddenCharacters(word);

            if (!word.isEmpty()) {
                wordOccurrences.put(word, wordOccurrences.getOrDefault(word, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(wordOccurrences.entrySet());
        sortedList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        System.out.println("\nТоп " + topWordsCount + " самых часто используемых слов:");

        for (int i = 0; i < Math.min(topWordsCount, sortedList.size()); i++) {
            Map.Entry<String, Integer> entry = sortedList.get(i);
            System.out.println(entry.getKey() + ": " + entry.getValue() + " раз");
        }

        if (showHistogram) {
            displayHistogram(wordOccurrences, topWordsCount);
        }
    }

    // Запись полной статистики в файл
    public void writeFullStatisticsToFile(String filePath, String outputFilePath) throws IOException {
        Scanner fileScanner = new Scanner(new File(filePath));
        Map<String, Integer> wordOccurrences = new HashMap<>();

        while (fileScanner.hasNext()) {
            String word = fileScanner.next().toLowerCase();

            word = removeForbiddenCharacters(word);

            if (!word.isEmpty()) {
                wordOccurrences.put(word, wordOccurrences.getOrDefault(word, 0) + 1);
            }
        }

        if (outputFilePath.isEmpty()) {
            outputFilePath = DEFAULT_OUTPUT_PATH;
        }

        try (PrintWriter writer = new PrintWriter(outputFilePath)) {
            writer.println("Статистика по словам в тексте:");

            for (Map.Entry<String, Integer> entry : wordOccurrences.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue() + " раз");
            }
        }

        Path absolutePath = Paths.get(outputFilePath).toAbsolutePath();
        System.out.println("Статистика записана в файл: " + absolutePath);
    }

    // Удаление запрещенных символов из текста
    private String removeForbiddenCharacters(String word) {
        for (char forbiddenChar : FORBIDDEN_CHARACTERS) {
            word = word.replace(String.valueOf(forbiddenChar), "");
        }
        return word;
    }

    // Конвертация doc/docx файла в txt
    private static String convertDocToTxt(String docFilePath) {
        String txtFilePath = docFilePath.replaceFirst("[.][^.]+$", ".txt");

        try {
            if (docFilePath.endsWith(".doc")) {
                FileInputStream fis = new FileInputStream(new File(docFilePath));
                HWPFDocument doc = new HWPFDocument(fis);
                WordExtractor we = new WordExtractor(doc);
                String content = we.getText();
                saveToFile(txtFilePath, content);
            } else if (docFilePath.endsWith(".docx")) {
                FileInputStream fis = new FileInputStream(new File(docFilePath));
                XWPFDocument doc = new XWPFDocument(fis);
                XWPFWordExtractor we = new XWPFWordExtractor(doc);
                String content = we.getText();
                saveToFile(txtFilePath, content);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return txtFilePath;
    }

    // Запись текста в файл
    private static void saveToFile(String filePath, String content) {
        try (PrintWriter writer = new PrintWriter(filePath)) {
            writer.println(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Проверка существования файла
    private static boolean isFileExists(String filePath) {
        File file = new File(filePath);
        return !file.exists() || !file.isFile();
    }
}