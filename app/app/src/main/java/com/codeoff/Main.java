package com.codeoff;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.*;
import java.awt.event.ActionEvent;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.swing.*;
import org.fife.rsta.ac.java.JarManager;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

public class Main {
    public static CardLayout cardLayout = new CardLayout();
    public static JPanel cardPanel = new JPanel(cardLayout);
    private static Client client;
    private static JTextArea subHeader;
    private static String uuid = "";
    private static RSyntaxTextArea textArea;
    private static JButton submitBtn;
    private static JLabel statusLabel;
    private static JLabel resultLabel;
    private static JLabel scoresLabel;
    private static JLabel eloLabel;
    private static JLabel gameHeader;
    private static JLabel timerLabel;
    private static javax.swing.Timer countdown;
    private static boolean timeUp = false;
    private static JLabel eloChangeLabel;
    private static JLabel feedbackLabel;
    private static JEditorPane helpPane;
    private static RTextScrollPane editorScrollPane;
    private static JavaLanguageSupport javaLanguageSupport;
    private static final Path UUID_FILE = Path.of("uuid.txt");
    private static final String DEFAULT_CODE =
        "public class Main {\n" +
        "    public static void main(String[] args) {\n" +
        "        \n" +
        "    }\n" +
        "}\n";

    public static void main(String[] args) {
        Settings.load();
        if (Settings.guiScale > 0) {
            System.setProperty("flatlaf.uiScale", String.valueOf(Settings.guiScale));
        }
        if (Settings.darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        while (true) {
            String error;
            try {
                client = new Client(new URI(Settings.serverAddress));
                if (client.connectBlocking()) {
                    break;
                }
                error = "Failed to connect to server.";
            } catch (Exception e) {
                error = "Failed to connect: " + e.getMessage();
            }

            Object newAddress = JOptionPane.showInputDialog(null, error + "\n\nServer address:", "Connection Error",
                JOptionPane.ERROR_MESSAGE, null, null, Settings.serverAddress);
            if (newAddress == null) {
                System.exit(1);
            }
            Settings.serverAddress = Settings.normalizeServerAddress(newAddress.toString());
            Settings.save();
        }

        JFrame frame = new JFrame("CodeOff");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(UIScale.scale(700), UIScale.scale(500));

        cardPanel.add(buildTitleScreen(), "TITLE");
        cardPanel.add(buildGameScreen(), "GAME");
        cardPanel.add(buildSettingsScreen(), "SETTINGS");
        cardPanel.add(buildMatchmakingScreen(), "MATCHMAKING");
        cardPanel.add(buildMatchFoundScreen(), "MATCHFOUND");
        cardPanel.add(buildResultsScreen(), "RESULTS");
        cardPanel.add(buildHelpScreen(), "HELP");

        frame.add(cardPanel);
        cardLayout.show(cardPanel, "TITLE");

        frame.setVisible(true);

        client.sendLogin(loadUUID());
    }

    private static JPanel buildTitleScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 100, 60, 100));

        JLabel title = new JLabel("CodeOff", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 36f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        eloLabel = new JLabel("Elo: -", SwingConstants.CENTER);
        eloLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton casualBtn = new JButton("Casual");
        JButton rankedBtn = new JButton("Ranked");
        JButton settingsBtn = new JButton("Settings");
        JButton helpBtn = new JButton("Help");
        JButton quitBtn = new JButton("Quit");

        for (JButton btn : new JButton[]{casualBtn, rankedBtn, settingsBtn, helpBtn, quitBtn}) {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(UIScale.scale(200), UIScale.scale(40)));
        }

        casualBtn.addActionListener((ActionEvent e) -> matchMake("casual"));
        rankedBtn.addActionListener((ActionEvent e) -> matchMake("ranked"));
        settingsBtn.addActionListener((ActionEvent e) ->
            cardLayout.show(cardPanel, "SETTINGS"));
        helpBtn.addActionListener((ActionEvent e) -> {
            cardLayout.show(cardPanel, "HELP");
            helpPane.setCaretPosition(0);
        });
        quitBtn.addActionListener((ActionEvent e) -> System.exit(0));

        panel.add(title);
        panel.add(eloLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 30)));
        panel.add(casualBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(rankedBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(settingsBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(helpBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(quitBtn);

        return panel;
    }

    private static JPanel buildGameScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        gameHeader = new JLabel("CodeOff", SwingConstants.CENTER);
        JLabel header = gameHeader;
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        subHeader = new JTextArea();
        subHeader.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12f));
        subHeader.setLineWrap(true);
        subHeader.setWrapStyleWord(true);
        subHeader.setEditable(false);
        subHeader.setFocusable(false);
        subHeader.setOpaque(false);
        subHeader.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(16), 0, UIScale.scale(16)));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        headerPanel.add(header, BorderLayout.NORTH);
        headerPanel.add(subHeader, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);

        textArea = new RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        editorScrollPane = scrollPane;
        panel.add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("", SwingConstants.LEFT);
        submitBtn = new JButton("Submit");
        submitBtn.addActionListener((ActionEvent e) -> submitSolution());

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        footerPanel.add(statusLabel, BorderLayout.CENTER);
        timerLabel = new JLabel("", SwingConstants.RIGHT);
        timerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        JPanel footerRight = new JPanel(new BorderLayout());
        footerRight.add(timerLabel, BorderLayout.WEST);
        footerRight.add(submitBtn, BorderLayout.EAST);
        footerPanel.add(footerRight, BorderLayout.EAST);
        panel.add(footerPanel, BorderLayout.SOUTH);

        LanguageSupportFactory lsf = LanguageSupportFactory.get();
        lsf.register(textArea);

        JavaLanguageSupport jls = (JavaLanguageSupport) lsf.getSupportFor(SyntaxConstants.SYNTAX_STYLE_JAVA);
        javaLanguageSupport = jls;
        JarManager jarManager = jls.getJarManager();
        try {
            jarManager.addCurrentJreClassFileSource();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        applyEditorSettings();

        return panel;
    }

    private static JPanel buildHelpScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Help", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        header.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        panel.add(header, BorderLayout.NORTH);

        helpPane = new JEditorPane("text/html", buildHelpHtml(null, 0));
        helpPane.setEditable(false);

        helpPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        helpPane.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        JScrollPane scrollPane = new JScrollPane(helpPane);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener((ActionEvent e) -> cardLayout.show(cardPanel, "TITLE"));
        JPanel backPanel = new JPanel();
        backPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
        backPanel.add(backBtn);
        panel.add(backPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static String buildHelpHtml(JsonArray ranks, int casualTimeLimit) {
        StringBuilder rankRows = new StringBuilder();
        if (ranks == null) {
            rankRows.append("<tr><td colspan='3'>Connecting to server...</td></tr>");
        } else {
            for (int i = 0; i < ranks.size(); i++) {
                JsonObject rank = ranks.get(i).getAsJsonObject();
                int minElo = rank.get("minElo").getAsInt();
                String eloRange;
                if (i + 1 == ranks.size()) {
                    eloRange = minElo + "+";
                } else {
                    int nextMinElo = ranks.get(i + 1).getAsJsonObject().get("minElo").getAsInt();
                    eloRange = i == 0 ? "under " + nextMinElo : minElo + " - " + (nextMinElo - 1);
                }
                rankRows.append("<tr><td><b>").append(rank.get("name").getAsString()).append("</b></td><td>")
                    .append(eloRange).append("</td><td>")
                    .append(formatMinutes(rank.get("timeLimit").getAsInt())).append("</td></tr>");
            }
        }
        String casualTime = ranks == null ? "a" : "a " + formatMinutes(casualTimeLimit);

        return "<html><body>"
            + "<h2>How to play</h2>"
            + "<p>Pick <b>Casual</b> or <b>Ranked</b> and you'll be matched against another player. "
            + "You both get the same Java coding challenge. Write your solution in the editor and hit <b>Submit</b> before the timer runs out.</p>"
            + "<p>You only get <b>one submission</b>, so test your logic before submitting!</p>"

            + "<h2>Scoring</h2>"
            + "<ul>"
            + "<li>Your code is compiled and run, then an AI judge gives it a score from 0% to 100%.</li>"
            + "<li>The judge looks at: following the prompt (ignoring it is an instant 0), correctness, efficiency, and code style.</li>"
            + "<li>Code that doesn't compile scores <b>0%</b>. The compile error is printed in the console.</li>"
            + "<li>You can write just the function, it gets wrapped in a class for you.</li>"
            + "<li>Not submitting before time runs out scores 0%.</li>"
            + "<li>The highest score wins. Equal scores are a draw.</li>"
            + "</ul>"

            + "<h2>Game modes</h2>"
            + "<p><b>Casual</b> - simple challenges with " + casualTime + " time limit. Your elo doesn't change.</p>"
            + "<p><b>Ranked</b> - you're matched with players close to your elo (the search widens the longer you wait). "
            + "Winning gains elo and losing loses it, beating someone higher rated gives more. "
            + "The challenge difficulty and time limit depend on the average rank of both players. "
            + "Leaving a ranked match counts as a loss.</p>"

            + "<h2>Ranks</h2>"
            + "<p>Everyone starts at 1000 elo. Higher ranks get harder challenges.</p>"
            + "<table cellpadding='4'><tr><th align='left'>Rank</th><th align='left'>Elo</th><th align='left'>Time limit</th></tr>"
            + rankRows
            + "</table>";
    }

    public static void setHelpInfo(JsonArray ranks, int casualTimeLimit) {
        SwingUtilities.invokeLater(() -> helpPane.setText(buildHelpHtml(ranks, casualTimeLimit)));
    }

    private static String formatMinutes(int seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + " min" : String.format("%d:%02d min", seconds / 60, seconds % 60);
    }

    private static JPanel buildSettingsScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Settings", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        header.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        panel.add(header, BorderLayout.NORTH);

        JComboBox<String> themeBox = new JComboBox<>(new String[]{"Dark", "Light"});
        themeBox.setSelectedIndex(Settings.darkMode ? 0 : 1);
        themeBox.addActionListener((ActionEvent e) -> {
            Settings.darkMode = themeBox.getSelectedIndex() == 0;
            Settings.save();
            SwingUtilities.invokeLater(Main::applyTheme);
        });

        String[] scaleNames = {"Auto", "100%", "125%", "150%", "175%", "200%", "250%"};
        double[] scaleValues = {0, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5};
        JComboBox<String> scaleBox = new JComboBox<>(scaleNames);
        for (int i = 0; i < scaleValues.length; i++) {
            if (scaleValues[i] == Settings.guiScale) {
                scaleBox.setSelectedIndex(i);
            }
        }
        scaleBox.addActionListener((ActionEvent e) -> {
            Settings.guiScale = scaleValues[scaleBox.getSelectedIndex()];
            Settings.save();
        });
        JLabel scaleNote = new JLabel("Takes effect next time you start the game");
        scaleNote.setFont(scaleNote.getFont().deriveFont(11f));
        scaleNote.setEnabled(false);

        JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(Settings.fontSize, Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE, 1));
        fontSizeSpinner.addChangeListener(e -> {
            Settings.fontSize = (int) fontSizeSpinner.getValue();
            Settings.save();
            applyEditorSettings();
        });

        JCheckBox wordWrapBox = new JCheckBox("Wrap long lines", Settings.wordWrap);
        wordWrapBox.addActionListener((ActionEvent e) -> {
            Settings.wordWrap = wordWrapBox.isSelected();
            Settings.save();
            applyEditorSettings();
        });

        JCheckBox completionBox = new JCheckBox("Show code completion popups", Settings.codeCompletion);
        completionBox.addActionListener((ActionEvent e) -> {
            Settings.codeCompletion = completionBox.isSelected();
            Settings.save();
            applyEditorSettings();
        });

        JCheckBox confirmBox = new JCheckBox("Ask before submitting a solution", Settings.confirmSubmit);
        confirmBox.addActionListener((ActionEvent e) -> {
            Settings.confirmSubmit = confirmBox.isSelected();
            Settings.save();
        });

        JTextField serverField = new JTextField(Settings.serverAddress, 22);
        JLabel serverNote = new JLabel("Takes effect next time you start the game");
        serverNote.setFont(serverNote.getFont().deriveFont(11f));
        serverNote.setEnabled(false);

        JPanel form = new JPanel(new GridBagLayout());
        addSettingsRow(form, 0, "Theme", themeBox);
        addSettingsRow(form, 1, "GUI scale", scaleBox);
        addSettingsRow(form, 2, "", scaleNote);
        addSettingsRow(form, 3, "Editor font size", fontSizeSpinner);
        addSettingsRow(form, 4, "Editor", wordWrapBox);
        addSettingsRow(form, 5, "", completionBox);
        addSettingsRow(form, 6, "Gameplay", confirmBox);
        addSettingsRow(form, 7, "Server address", serverField);
        addSettingsRow(form, 8, "", serverNote);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.add(form, BorderLayout.NORTH);
        panel.add(formWrapper, BorderLayout.CENTER);

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener((ActionEvent e) -> {
            String address = Settings.normalizeServerAddress(serverField.getText());
            serverField.setText(address);
            if (!address.equals(Settings.serverAddress)) {
                Settings.serverAddress = address;
                Settings.save();
            }
            cardLayout.show(cardPanel, "TITLE");
        });
        JPanel backPanel = new JPanel();
        backPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
        backPanel.add(backBtn);
        panel.add(backPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static void addSettingsRow(JPanel form, int row, String label, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        form.add(new JLabel(label), c);

        c.gridx = 1;
        form.add(component, c);
    }

    public static void applyTheme() {
        if (Settings.darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI();
        applyEditorSettings();
    }

    public static void applyEditorSettings() {
        try {
            Theme theme = Theme.load(Main.class.getResourceAsStream(
                Settings.darkMode ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml" : "/org/fife/ui/rsyntaxtextarea/themes/idea.xml"));
            theme.apply(textArea);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Font font = textArea.getFont().deriveFont((float) Settings.fontSize);
        textArea.setFont(font);
        editorScrollPane.getGutter().setLineNumberFont(font);

        textArea.setLineWrap(Settings.wordWrap);
        textArea.setWrapStyleWord(true);

        javaLanguageSupport.setAutoCompleteEnabled(Settings.codeCompletion);
        javaLanguageSupport.setParameterAssistanceEnabled(Settings.codeCompletion);
    }

    private static JPanel buildMatchmakingScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Matchmaking", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        panel.add(header, BorderLayout.NORTH);

        JLabel searching = new JLabel("Searching for an opponent...", SwingConstants.CENTER);
        panel.add(searching, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener((ActionEvent e) -> client.sendCancelMatchmaking());
        JPanel cancelPanel = new JPanel();
        cancelPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        cancelPanel.add(cancelBtn);
        panel.add(cancelPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static JPanel buildMatchFoundScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Match Found!", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        panel.add(header, BorderLayout.NORTH);

        JLabel generating = new JLabel("Generating challenge...", SwingConstants.CENTER);
        panel.add(generating, BorderLayout.CENTER);

        return panel;
    }

    private static JPanel buildResultsScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 100, 60, 100));

        JLabel header = new JLabel("Match Over", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 36f));

        resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 20f));

        scoresLabel = new JLabel("", SwingConstants.CENTER);
        eloChangeLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setFont(feedbackLabel.getFont().deriveFont(Font.ITALIC));

        JButton menuBtn = new JButton("Back to Menu");
        menuBtn.setMaximumSize(new Dimension(UIScale.scale(200), UIScale.scale(40)));
        menuBtn.addActionListener((ActionEvent e) -> cardLayout.show(cardPanel, "TITLE"));

        for (JComponent c : new JComponent[]{header, resultLabel, scoresLabel, feedbackLabel, eloChangeLabel, menuBtn}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        panel.add(header);
        panel.add(Box.createRigidArea(new Dimension(0, 30)));
        panel.add(resultLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(scoresLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(eloChangeLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(feedbackLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 30)));
        panel.add(menuBtn);

        return panel;
    }

    private static void matchMake(String mode) {
        if (getUUID().isEmpty()) {
            JOptionPane.showMessageDialog(cardPanel, "Still logging in, try again in a second.", "Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }
        client.sendMatchmake(mode, getUUID());
    }

    private static void submitSolution() {
        String code = textArea.getText();
        if (code.isBlank() || code.strip().equals(DEFAULT_CODE.strip())) {
            statusLabel.setText("Write some code first!");
            return;
        }
        if (client.getMatchId() == null) {
            statusLabel.setText("No active match.");
            return;
        }
        if (Settings.confirmSubmit) {
            int choice = JOptionPane.showConfirmDialog(cardPanel, "You only get one submission. Submit now?", "Submit Solution", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        submitBtn.setEnabled(false);
        statusLabel.setText("Grading...");
        client.sendSolution(code);
    }

    public static void setPrompt(String prompt) {
        SwingUtilities.invokeLater(() -> subHeader.setText(prompt));
    }

    public static void resetGame() {
        SwingUtilities.invokeLater(() -> {
            textArea.setText(DEFAULT_CODE);
            textArea.setCaretPosition(DEFAULT_CODE.indexOf("args) {") + "args) {\n".length() + 8);
            textArea.requestFocusInWindow();
            textArea.discardAllEdits();
            submitBtn.setEnabled(true);
            statusLabel.setText("");
            statusLabel.setToolTipText(null);
            feedbackLabel.setText("");
            timeUp = false;
        });
    }

    public static void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel.getText().equals("Grading...")) {
                submitBtn.setEnabled(!timeUp);
                statusLabel.setText("Error: " + message);
            } else {
                JOptionPane.showMessageDialog(cardPanel, message, "Server Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static void showSolutionResult(String playerUUID, double score, String feedback, String compileError) {
        SwingUtilities.invokeLater(() -> {
            String percent = formatScore(score);
            if (playerUUID.equals(uuid)) {
                statusLabel.setText("Your score: " + percent + (compileError != null ? " (didn't compile)" : "") + " - waiting for opponent...");
                if (compileError != null) {
                    System.out.println("Compile error:\n" + compileError);
                }
                if (feedback != null) {
                    statusLabel.setToolTipText(feedback);
                    setFeedback(feedback);
                } else if (compileError != null) {
                    setFeedback("Your code didn't compile (the error is printed in the console)");
                }
            } else if (submitBtn.isEnabled()) {
                statusLabel.setText("Opponent submitted! (" + percent + ")");
            }
        });
    }

    public static void showMatchOver(JsonObject data) {
        String reason = data.get("reason").getAsString();
        String winner = data.get("winner").isJsonNull() ? null : data.get("winner").getAsString();
        JsonObject scores = data.getAsJsonObject("scores");

        String result;
        if (reason.equals("error")) {
            result = "Match cancelled";
        } else if (reason.equals("opponent_left")) {
            result = "Opponent left - You Win!";
        } else if (winner == null) {
            result = "Draw!";
        } else if (winner.equals(uuid)) {
            result = "You Win!";
        } else {
            result = "You Lose!";
        }

        String yourScore = "-";
        String opponentScore = "-";
        for (String key : scores.keySet()) {
            if (key.equals(uuid)) {
                yourScore = formatScore(scores.get(key).getAsDouble());
            } else {
                opponentScore = formatScore(scores.get(key).getAsDouble());
            }
        }
        String scoreText = "You: " + yourScore + "   Opponent: " + opponentScore;

        String eloText = "";
        JsonObject elo = data.getAsJsonObject("elo");
        if (elo != null && elo.has(uuid)) {
            int oldElo = elo.getAsJsonObject(uuid).get("old").getAsInt();
            int newElo = elo.getAsJsonObject(uuid).get("new").getAsInt();
            String oldRank = elo.getAsJsonObject(uuid).get("oldRank").getAsString();
            String newRank = elo.getAsJsonObject(uuid).get("newRank").getAsString();
            int change = newElo - oldElo;
            eloText = "Elo: " + oldElo + " -> " + newElo + " (" + (change >= 0 ? "+" : "") + change + ")";
            if (!oldRank.equals(newRank)) {
                eloText += "   " + oldRank + " -> " + newRank + "!";
            } else {
                eloText += "   " + newRank;
            }
            setElo(newElo, newRank);
        }
        String finalEloText = eloText;
        String feedback = data.has("feedback") && !data.get("feedback").isJsonNull() ? data.get("feedback").getAsString() : null;

        SwingUtilities.invokeLater(() -> {
            stopCountdown();
            resultLabel.setText(result);
            scoresLabel.setText(scoreText);
            eloChangeLabel.setText(finalEloText);
            if (feedback != null) {
                setFeedback(feedback);
            }
            cardLayout.show(cardPanel, "RESULTS");
        });
    }

    public static void startCountdown(int seconds) {
        SwingUtilities.invokeLater(() -> {
            stopCountdown();
            long endTime = System.currentTimeMillis() + seconds * 1000L;
            countdown = new javax.swing.Timer(250, (ActionEvent e) -> {
                long left = Math.max(0, (endTime - System.currentTimeMillis() + 999) / 1000);
                timerLabel.setText(String.format("%d:%02d", left / 60, left % 60));
            });
            countdown.setInitialDelay(0);
            countdown.start();
        });
    }

    public static void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    public static void onTimeUp() {
        SwingUtilities.invokeLater(() -> {
            timeUp = true;
            stopCountdown();
            timerLabel.setText("0:00");
            if (submitBtn.isEnabled()) {
                submitBtn.setEnabled(false);
                statusLabel.setText("Time's up!");
            }
        });
    }

    private static void setFeedback(String text) {
        feedbackLabel.setText("<html><div style='text-align:center;width:400px'>" + escapeHtml(text) + "</div></html>");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String formatScore(double score) {
        return Math.round(score * 100) + "%";
    }

    public static String getUUID() {
        return uuid;
    }

    public static void setUUID(String newUUID) {
        uuid = newUUID;
    }

    public static void setElo(int elo, String rank) {
        SwingUtilities.invokeLater(() -> eloLabel.setText(rank + " - Elo: " + elo));
    }

    public static void setMatchRank(String rank) {
        SwingUtilities.invokeLater(() -> gameHeader.setText(rank == null ? "CodeOff - Casual" : "CodeOff - Ranked (" + rank + ")"));
    }

    private static String loadUUID() {
        try {
            if (Files.exists(UUID_FILE)) {
                String saved = Files.readString(UUID_FILE).trim();
                return saved.isEmpty() ? null : saved;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void saveUUID(String newUUID) {
        try {
            Files.writeString(UUID_FILE, newUUID);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}
