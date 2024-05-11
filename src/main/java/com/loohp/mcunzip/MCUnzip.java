package com.loohp.mcunzip;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import net.harawata.appdirs.AppDirsFactory;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("UnusedReturnValue")
public class MCUnzip {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0");

    static {
        DECIMAL_FORMAT.setRoundingMode(RoundingMode.FLOOR);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
            launchModeGUI();
        } else {
            launchModeCLI(args);
        }
    }

    public static void launchModeGUI() throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        BufferedImage image = ImageIO.read(MCUnzip.class.getClassLoader().getResourceAsStream("icon.png"));
        Platform.startup(() -> {
            File selectedFile = null;
            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose Zipped Resource Pack to Extract");
                fileChooser.getExtensionFilters().addAll(new ExtensionFilter("Zip Files", "*.zip"), new ExtensionFilter("All Files", "*.*"));

                Properties properties = new Properties();
                File defaultDirectory = new File(".");
                File storageDirectory = new File(AppDirsFactory.getInstance().getUserDataDir(".mcunzip", ".", ".", true));
                if (!storageDirectory.exists()) {
                    storageDirectory.mkdirs();
                }
                File propertiesFile = new File(storageDirectory, "mcunzip.properties");
                if (propertiesFile.exists()) {
                    properties.load(new FileInputStream(propertiesFile));
                    String path = properties.getProperty("last-directory");
                    try {
                        File lastDirection = new File(path);
                        if (lastDirection.exists()) {
                            defaultDirectory = new File(path);
                        }
                    } catch (Throwable ignore) {
                    }
                } else {
                    properties.setProperty("last-directory", ".");
                }

                fileChooser.setInitialDirectory(defaultDirectory);
                selectedFile = fileChooser.showOpenDialog(null);
                if (selectedFile == null) {
                    System.exit(0);
                }

                String title = "Extracting " + selectedFile.getName();
                JFrame frame = new JFrame(title);
                frame.setIconImage(image);
                frame.setSize(800, 175);
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                frame.setLocationRelativeTo(null);

                JPanel panel = new JPanel();
                panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
                panel.setLayout(new GridLayout(0, 1));

                JLabel label = createLabel("<html>Counting Entries<br><html/>", 15);
                label.setSize(800, 125);
                panel.add(label);

                JProgressBar progressBar = new JProgressBar(0, 100);
                panel.add(progressBar);

                frame.add(panel, BorderLayout.CENTER);
                frame.setResizable(false);
                frame.setVisible(true);

                File extractedFolder = extract(selectedFile, (percentage, zipFile, zipEntry) -> {
                    String name = zipEntry.getName();
                    if (name.equals("pack.png")) {
                        try {
                            frame.setIconImage(ImageIO.read(zipFile.getInputStream(zipEntry)));
                        } catch (IOException e) {
                            frame.setIconImage(image);
                        }
                    }
                    percentage = percentage * 100;
                    label.setText("<html>Extracting " + name + "<br>Completed " + DECIMAL_FORMAT.format(percentage) + "%<html/>");
                    progressBar.setValue(Math.min(100, (int) Math.round(percentage)));
                });

                if (selectedFile.getParentFile() != null) {
                    properties.setProperty("last-directory", selectedFile.getParentFile().getAbsolutePath());
                }
                properties.store(new FileOutputStream(propertiesFile), null);

                progressBar.setValue(100);

                File packIconFile = new File(extractedFolder, "pack.png");
                Icon icon = null;
                try {
                    if (packIconFile.exists()) {
                        icon = new ImageIcon(ImageIO.read(packIconFile).getScaledInstance(128, 128, Image.SCALE_DEFAULT));
                    }
                } catch (Throwable ignore) {
                }
                if (icon == null) {
                    icon = new ImageIcon(image);
                }

                JOptionPane.showMessageDialog(null, createLabel("Resource Pack extracted at " + extractedFolder.getAbsolutePath(), 15), title, JOptionPane.INFORMATION_MESSAGE, icon);

                frame.setVisible(false);
                frame.dispose();
            } catch (Throwable e) {
                e.printStackTrace();
                if (selectedFile != null) {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null, createLabel("Unable to extract " + selectedFile.getName() + ": " + e.getClass().getName() + ": " + e.getLocalizedMessage(), 13, Color.RED), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            System.exit(0);
        });
    }

    public static void launchModeCLI(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Please specify which zipped resource pack file to extract.");
            return;
        }
        File inputFile = new File(args[0]);
        if (!inputFile.exists()) {
            System.out.println("The specified zipped resource pack file \"" + inputFile.getAbsolutePath() + "\" does not exist.");
            return;
        }
        File extractedFolder = extract(inputFile, (percentage, zipFile, zipEntry) -> {
            String name = zipEntry.getName();
            percentage = percentage * 100;
            System.out.println(DECIMAL_FORMAT.format(percentage) + "% - Extracting: " + name);
        });

        for (int i = 0; i < 10; i++) {
            System.out.println();
        }
        System.out.println("Resource Pack extracted at " + extractedFolder.getAbsolutePath());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File extract(File zipFile, TriConsumer<Double, ZipFile, ZipEntry> progressListener) throws Exception {
        String folderName = zipFile.getName();
        if (folderName.contains(".")) {
            folderName = folderName.substring(0, folderName.indexOf("."));
        }
        File resourceFile = new File(zipFile.getParent(), folderName);
        for (int i = 2;; i++) {
            if (resourceFile.exists()) {
                resourceFile = new File(zipFile.getParent(), folderName + " (" + i + ")");
            } else {
                break;
            }
        }
        try (ZipFile zip = new ZipFile(zipFile)) {
            int totalEntries = size(zip.entries());
            int count = 0;
            Enumeration<? extends ZipEntry> itr = zip.entries();
            while (itr.hasMoreElements()) {
                ZipEntry entry = itr.nextElement();
                progressListener.accept((double) ++count / (double) totalEntries, zip, entry);
                String name = entry.getName();
                if (entry.isDirectory()) {
                    File folder = new File(resourceFile, name).getParentFile();
                    folder.mkdirs();
                } else {
                    String fileName = getEntryName(name);
                    InputStream in = zip.getInputStream(entry);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] byteChunk = new byte[4096];
                    int n;
                    while ((n = in.read(byteChunk)) > 0) {
                        baos.write(byteChunk, 0, n);
                    }
                    byte[] currentEntry = baos.toByteArray();

                    File folder = new File(resourceFile, name).getParentFile();
                    folder.mkdirs();
                    File file = new File(folder, fileName);
                    if (file.exists()) {
                        file.delete();
                    }
                    copy(new ByteArrayInputStream(currentEntry), file);
                }
            }
        }
        return resourceFile;
    }

    private static int size(Enumeration<?> enumeration) {
        int i = 0;
        while (enumeration.hasMoreElements()) {
            enumeration.nextElement();
            i++;
        }
        return i;
    }

    private static JLabel createLabel(String message, float fontSize) {
        return createLabel(message, fontSize, Color.BLACK);
    }

    private static JLabel createLabel(String message, float fontSize, Color color) {
        JLabel label = new JLabel("<html>" + message.replace("\n", "<br>") + "<html/>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN).deriveFont(fontSize));
        label.setForeground(color);
        return label;
    }

    private static long copy(InputStream from, File to) throws IOException {
        return Files.copy(from, to.toPath());
    }

    private static String getEntryName(String name) {
        int pos = name.lastIndexOf("/");
        if (pos >= 0) {
            return name.substring(pos + 1);
        }
        pos = name.lastIndexOf("\\");
        if (pos >= 0) {
            return name.substring(pos + 1);
        }
        return name;
    }

    public interface TriConsumer<T, U, V> {

        void accept(T t, U u, V v);

    }

}
