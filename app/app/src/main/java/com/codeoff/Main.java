package com.codeoff;

import java.net.URI;
import java.awt.*;
import java.awt.event.ActionEvent;
import com.formdev.flatlaf.FlatDarkLaf;
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
    private static JLabel subHeader;

    public static void main(String[] args) {
        FlatDarkLaf.setup(); // or FlatLightLaf

        try {
            client = new Client(new URI("ws://localhost:3000/"));
            boolean connected = client.connectBlocking();
            if (!connected) {
                JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to connect: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        JFrame frame = new JFrame("CodeOff");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);

        cardPanel.add(buildTitleScreen(), "TITLE");
        cardPanel.add(buildGameScreen(), "GAME");
        cardPanel.add(buildSettingsScreen(), "SETTINGS");
        cardPanel.add(buildMatchmakingScreen(), "MATCHMAKING");
        cardPanel.add(buildMatchFoundScreen(), "MATCHFOUND");

        frame.add(cardPanel);
        cardLayout.show(cardPanel, "TITLE");

        frame.setVisible(true);
    }

    private static JPanel buildTitleScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 100, 60, 100));

        JLabel title = new JLabel("CodeOff", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 36f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton casualBtn = new JButton("Casual");
        JButton rankedBtn = new JButton("Ranked");
        JButton settingsBtn = new JButton("Settings");
        JButton quitBtn = new JButton("Quit");

        rankedBtn.setEnabled(false);

        for (JButton btn : new JButton[]{casualBtn, rankedBtn, settingsBtn, quitBtn}) {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 40));
        }

        casualBtn.addActionListener((ActionEvent e) -> matchMake());
        rankedBtn.addActionListener((ActionEvent e) -> matchMake());
        settingsBtn.addActionListener((ActionEvent e) ->
            cardLayout.show(cardPanel, "SETTINGS"));
        quitBtn.addActionListener((ActionEvent e) -> System.exit(0));

        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0, 40)));
        panel.add(casualBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(rankedBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(settingsBtn);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(quitBtn);

        return panel;
    }

    private static JPanel buildGameScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("CodeOff", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        subHeader = new JLabel("make a simple for loop", SwingConstants.CENTER);
        subHeader.setFont(subHeader.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        subHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(header);
        headerPanel.add(subHeader);

        panel.add(headerPanel, BorderLayout.NORTH);

        RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);

        try {
            Theme theme = Theme.load(Main.class.getResourceAsStream(
                "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(textArea);
        } catch (Exception e) {
            e.printStackTrace();
        }

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        LanguageSupportFactory lsf = LanguageSupportFactory.get();
        lsf.register(textArea);

        JavaLanguageSupport jls = (JavaLanguageSupport) lsf.getSupportFor(SyntaxConstants.SYNTAX_STYLE_JAVA);
        JarManager jarManager = jls.getJarManager();
        try {
            jarManager.addCurrentJreClassFileSource();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        return panel;
    }

    private static JPanel buildSettingsScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Settings", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        
        
        panel.add(header, BorderLayout.NORTH);

        return panel;
    }

    private static JPanel buildMatchmakingScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Matchmaking", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        panel.add(header, BorderLayout.NORTH);

        
        return panel;
    }

    private static JPanel buildMatchFoundScreen() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel header = new JLabel("Match Found!", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));

        panel.add(header, BorderLayout.NORTH);

        return panel;
    }

    private static void matchMake() {
        client.sendMatchmake("casual", "some-uuid");
    }

    public static void setPrompt(String prompt) {
        SwingUtilities.invokeLater(() -> subHeader.setText(prompt));
    }

    // public static void applySettings(boolean darkMode) {
    //     if (darkMode) {
    //         FlatDarkLaf.setup();
    //         // try {
    //         //     Theme theme = Theme.load(Main.class.getResourceAsStream(
    //         //         "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
    //         //     theme.apply(textArea);
    //         // } catch (Exception e) {
    //         //     e.printStackTrace();
    //         // }
    //     } else {
    //         FlatLightLaf.setup();
    //         // try {
    //         //    Theme theme = Theme.load(Main.class.getResourceAsStream(
    //         //         "/org/fife/ui/rsyntaxtextarea/themes/light.xml"));
    //         //     theme.apply(textArea);
    //         // } catch (Exception e) {
    //         //     e.printStackTrace();
    //         // }
    //     }
    //     SwingUtilities.updateComponentTreeUI(cardPanel);
    // }
}