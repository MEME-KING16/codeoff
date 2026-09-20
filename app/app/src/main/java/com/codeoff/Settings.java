package com.codeoff;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Settings {
    private static final Path SETTINGS_FILE = Path.of("settings.properties");
    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 36;

    public static boolean darkMode = true;
    public static double guiScale = 0;
    public static int fontSize = 14;
    public static boolean wordWrap = false;
    public static boolean codeCompletion = true;
    public static boolean confirmSubmit = true;
    public static String serverAddress = "ws://localhost:3000/";

    public static void load() {
        if (!Files.exists(SETTINGS_FILE)) {
            return;
        }

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(SETTINGS_FILE)) {
            props.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        darkMode = Boolean.parseBoolean(props.getProperty("darkMode", String.valueOf(darkMode)));
        try {
            double scale = Double.parseDouble(props.getProperty("guiScale", String.valueOf(guiScale)));
            guiScale = scale <= 0 ? 0 : Math.clamp(scale, 1.0, 3.0);
        } catch (NumberFormatException e) {
        }
        wordWrap = Boolean.parseBoolean(props.getProperty("wordWrap", String.valueOf(wordWrap)));
        codeCompletion = Boolean.parseBoolean(props.getProperty("codeCompletion", String.valueOf(codeCompletion)));
        confirmSubmit = Boolean.parseBoolean(props.getProperty("confirmSubmit", String.valueOf(confirmSubmit)));
        serverAddress = normalizeServerAddress(props.getProperty("serverAddress", serverAddress));
        try {
            fontSize = Math.clamp(Integer.parseInt(props.getProperty("fontSize", String.valueOf(fontSize))), MIN_FONT_SIZE, MAX_FONT_SIZE);
        } catch (NumberFormatException e) {
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("darkMode", String.valueOf(darkMode));
        props.setProperty("guiScale", String.valueOf(guiScale));
        props.setProperty("fontSize", String.valueOf(fontSize));
        props.setProperty("wordWrap", String.valueOf(wordWrap));
        props.setProperty("codeCompletion", String.valueOf(codeCompletion));
        props.setProperty("confirmSubmit", String.valueOf(confirmSubmit));
        props.setProperty("serverAddress", serverAddress);

        try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
            props.store(writer, "CodeOff settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String normalizeServerAddress(String address) {
        address = address.trim();
        if (address.isEmpty()) {
            return "ws://localhost:3000/";
        }
        if (!address.contains("://")) {
            address = "ws://" + address;
        }
        return address;
    }
}
