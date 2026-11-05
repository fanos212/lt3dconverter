package lt3d;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import lt3d.core.ImportException;
import lt3d.core.ImportResult;
import lt3d.core.ImportSettings;
import lt3d.core.Job;
import lt3d.core.LittleTilesFormat;
import lt3d.core.ModelConverter;

/** The window of the program. Without arguments it opens the window, with arguments it runs the command line mode. */
public final class App extends JFrame {

    public static void main(String[] args) {
        if (args.length > 0) {
            System.exit(Cli.run(args));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new App().setVisible(true);
        });
    }

    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private final Preferences prefs = Preferences.userNodeForPackage(App.class);

    private final JTextField modelField = new JTextField(34);
    private final JTextField textureField = new JTextField(34);
    private final JButton textureBrowse = new JButton("Обзор…");
    private final JRadioButton rbColor = new JRadioButton("Один цвет", true);
    private final JRadioButton rbTexture = new JRadioButton("Текстура (картинка)");
    private final JRadioButton rbMtl = new JRadioButton("Материалы из .mtl");
    private final JButton colorButton = new JButton("   ");
    private Color color = Color.WHITE;
    private final JComboBox<String> modeBox = new JComboBox<>(new String[] { "Срезы — быстро, гладкие поверхности", "Воксели — кубики, цвет по текстуре" });
    private final JComboBox<Integer> gridBox = new JComboBox<>(new Integer[] { 1, 2, 4, 8, 16, 32, 64, 128 });
    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(64, 1, 100000, 8));
    private final JSpinner precisionSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.05, 4.0, 0.05));
    private final JTextField blockField = new JTextField("littletiles:colored_clean", 24);
    private final JSlider cpuSlider = new JSlider(5, 100, 75);
    private final JLabel cpuLabel = new JLabel();

    private final JButton startButton = new JButton("Конвертировать");
    private final JButton cancelButton = new JButton("Отмена");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel phaseLabel = new JLabel(" ");
    private final JLabel timeLabel = new JLabel(" ");
    private final JLabel resultLabel = new JLabel(" ");
    private final JLabel warnLabel = new JLabel(" ");
    private final JTextArea preview = new JTextArea(5, 60);
    private final JButton copyButton = new JButton("Копировать в буфер обмена");
    private final JButton saveButton = new JButton("Сохранить в .txt…");

    private volatile Job job;
    private String resultText;
    private final Timer timer = new Timer(250, e -> tick());

    // ETA smoothing
    private String lastPhase = "";
    private long lastDone;
    private long lastNanos;
    private double rate; // items per second, smoothed

    private App() {
        super("LT 3D Converter — OBJ → LittleTiles");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        gridBox.setSelectedItem(16);
        modeBox.addActionListener(e -> updateEnabled());

        ButtonGroup group = new ButtonGroup();
        group.add(rbColor);
        group.add(rbTexture);
        group.add(rbMtl);
        rbColor.addActionListener(e -> updateEnabled());
        rbTexture.addActionListener(e -> updateEnabled());
        rbMtl.addActionListener(e -> updateEnabled());

        colorButton.setBackground(color);
        colorButton.setOpaque(true);
        colorButton.addActionListener(e -> {
            Color picked = JColorChooser.showDialog(this, "Цвет модели", color);
            if (picked != null) {
                color = picked;
                colorButton.setBackground(color);
            }
        });

        java.util.Hashtable<Integer, JLabel> labels = new java.util.Hashtable<>();
        for (int v : new int[] { 5, 25, 50, 75, 100 })
            labels.put(v, new JLabel(v + "%"));
        cpuSlider.setLabelTable(labels);
        cpuSlider.setMinorTickSpacing(5);
        cpuSlider.setPaintTicks(true);
        cpuSlider.setPaintLabels(true);
        cpuSlider.addChangeListener(e -> {
            cpuChanged();
        });

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridwidth = 1;

        int y = 0;
        // ---- model
        JPanel modelPanel = titled("Модель");
        JButton browse = new JButton("Обзор…");
        browse.addActionListener(e -> {
            File f = choose("Выберите .obj", new FileNameExtensionFilter("Wavefront OBJ (*.obj)", "obj"), modelField.getText());
            if (f != null) {
                modelField.setText(f.getAbsolutePath());
                prefs.put("dir", f.getParent());
            }
        });
        addRow(modelPanel, 0, "Файл .obj:", modelField, browse);
        c.gridy = y++;
        content.add(modelPanel, c);

        // ---- colour
        JPanel colorPanel = titled("Цвет");
        JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        radios.add(rbColor);
        radios.add(colorButton);
        radios.add(rbTexture);
        radios.add(rbMtl);
        addRow(colorPanel, 0, "Источник:", radios, null);
        textureBrowse.addActionListener(e -> {
            File f = choose("Выберите картинку", new FileNameExtensionFilter("Изображения (png, jpg, bmp)", "png", "jpg", "jpeg", "bmp"), textureField.getText());
            if (f != null)
                textureField.setText(f.getAbsolutePath());
        });
        addRow(colorPanel, 1, "Текстура:", textureField, textureBrowse);
        c.gridy = y++;
        content.add(colorPanel, c);

        // ---- parameters
        JPanel param = titled("Параметры");
        addRow(param, 0, "Алгоритм:", modeBox, null);
        addRow(param, 1, "Сетка (тайлов в блоке):", gridBox, null);
        addRow(param, 2, "Макс. размер (в тайлах):", sizeSpinner, null);
        addRow(param, 3, "Точность (для вокселей):", precisionSpinner, null);
        addRow(param, 4, "Блок:", blockField, null);
        c.gridy = y++;
        content.add(param, c);

        // ---- resources
        JPanel res = titled("Ресурсы компьютера");
        addRow(res, 0, "Нагрузка на ПК:", cpuSlider, null);
        addRow(res, 1, "", cpuLabel, null);
        c.gridy = y++;
        content.add(res, c);

        // ---- run
        JPanel run = titled("Выполнение");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(startButton);
        buttons.add(cancelButton);
        addRow(run, 0, "", buttons, null);
        bar.setStringPainted(true);
        addRow(run, 1, "", bar, null);
        addRow(run, 2, "", phaseLabel, null);
        addRow(run, 3, "", timeLabel, null);
        c.gridy = y++;
        content.add(run, c);

        // ---- result
        JPanel out = titled("Результат");
        addRow(out, 0, "", resultLabel, null);
        warnLabel.setForeground(new Color(0xB05A00));
        addRow(out, 1, "", warnLabel, null);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addRow(out, 2, "", new JScrollPane(preview), null);
        JPanel outButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        outButtons.add(copyButton);
        outButtons.add(saveButton);
        addRow(out, 3, "", outButtons, null);
        JLabel hint = new JLabel("<html>В игре: Little Importer → вставьте текст в поле → «Import».</html>");
        addRow(out, 4, "", hint, null);
        c.gridy = y++;
        content.add(out, c);

        startButton.addActionListener(e -> start());
        cancelButton.addActionListener(e -> {
            if (job != null)
                job.cancel();
        });
        copyButton.addActionListener(e -> copy());
        saveButton.addActionListener(e -> save());

        setContentPane(new JScrollPane(content));
        cpuChanged();
        updateEnabled();
        setRunning(false);
        copyButton.setEnabled(false);
        saveButton.setEnabled(false);
        pack();
        setSize(Math.max(getWidth() + 20, 720), Math.min(getHeight() + 20, 860));
        setLocationRelativeTo(null);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    private void cpuChanged() {
        int p = cpuSlider.getValue();
        double cores = CORES * p / 100D;
        cpuLabel.setText(String.format("%d%%  ≈ %.1f из %d ядер процессора", p, cores, CORES));
        if (job != null)
            job.cpuFraction = p / 100D; // takes effect while the conversion runs
    }

    private void updateEnabled() {
        boolean voxel = modeBox.getSelectedIndex() == 1;
        precisionSpinner.setEnabled(voxel);
        colorButton.setEnabled(rbColor.isSelected());
        textureField.setEnabled(rbTexture.isSelected());
        textureBrowse.setEnabled(rbTexture.isSelected());
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        for (JComponent comp : new JComponent[] { modelField, modeBox, gridBox, sizeSpinner, blockField, rbColor, rbTexture, rbMtl })
            comp.setEnabled(!running);
        if (!running)
            updateEnabled();
        else {
            precisionSpinner.setEnabled(false);
            colorButton.setEnabled(false);
            textureField.setEnabled(false);
            textureBrowse.setEnabled(false);
        }
    }

    private void start() {
        ImportSettings s = new ImportSettings();
        s.modelFile = modelField.getText();
        if (s.modelFile.isBlank()) {
            error("Выберите файл .obj");
            return;
        }
        s.mode = modeBox.getSelectedIndex() == 1 ? ImportSettings.Mode.VOXELS : ImportSettings.Mode.SLICES;
        s.grid = (Integer) gridBox.getSelectedItem();
        s.maxSize = ((Number) sizeSpinner.getValue()).intValue();
        s.precision = ((Number) precisionSpinner.getValue()).floatValue();
        s.baseBlock = blockField.getText().trim();
        s.color = 0xFF000000 | (color.getRGB() & 0xFFFFFF);
        s.useTexture = rbTexture.isSelected() || rbMtl.isSelected();
        s.useMtl = rbMtl.isSelected();
        s.textureFile = textureField.getText();

        Job j = Job.allCores();
        j.cpuFraction = cpuSlider.getValue() / 100D;
        job = j;
        lastPhase = "";
        rate = 0;
        resultText = null;
        resultLabel.setText(" ");
        warnLabel.setText(" ");
        preview.setText("");
        copyButton.setEnabled(false);
        saveButton.setEnabled(false);
        setRunning(true);
        timer.start();

        Thread t = new Thread(() -> {
            try {
                ImportResult r = ModelConverter.convert(s, j);
                j.phase("Writing", 0);
                LittleTilesFormat.Output out = LittleTilesFormat.toSnbt(r, s);
                SwingUtilities.invokeLater(() -> finished(out, r, j));
            } catch (Job.Cancelled e) {
                SwingUtilities.invokeLater(() -> stopped("Отменено"));
            } catch (ImportException e) {
                SwingUtilities.invokeLater(() -> {
                    stopped(null);
                    error(translate(e.getMessage()));
                });
            } catch (Throwable e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    stopped(null);
                    error(e instanceof OutOfMemoryError ? "Не хватило памяти. Уменьшите макс. размер/сетку или используйте режим «Срезы»." : "Ошибка: " + e);
                });
            }
        }, "lt3d-main");
        t.setDaemon(true);
        t.start();
    }

    private void stopped(String message) {
        timer.stop();
        setRunning(false);
        bar.setValue(0);
        bar.setIndeterminate(false);
        bar.setString(message == null ? "" : message);
        phaseLabel.setText(message == null ? " " : message);
        timeLabel.setText(" ");
    }

    private void finished(LittleTilesFormat.Output out, ImportResult r, Job j) {
        timer.stop();
        setRunning(false);
        bar.setIndeterminate(false);
        bar.setValue(1000);
        bar.setString("Готово");
        phaseLabel.setText("Готово");
        timeLabel.setText(String.format("Заняло %s", duration(j.totalSeconds())));
        resultText = out.text;
        double kb = out.text.length() / 1024D;
        String sizeText = kb > 1024 ? String.format("%.2f МБ", kb / 1024) : String.format("%.0f КБ", kb);
        resultLabel.setText(String.format("<html>Боксов: <b>%d</b>, цветов: %d, размер модели: %d×%d×%d тайлов, текст: %s%s</html>", out.boxes, out.tiles, out.size[0], out.size[1],
            out.size[2], sizeText, r.skippedTriangles > 0 ? ", пропущено слишком мелких треугольников: " + r.skippedTriangles : ""));
        warnLabel.setText(kb > 2048 ? "<html>Очень большой текст. Если игра не принимает вставку или зависает — уменьшите «Макс. размер» или выберите сетку поменьше.</html>" : " ");
        preview.setText(out.text.length() > 1500 ? out.text.substring(0, 1500) + " …" : out.text);
        preview.setCaretPosition(0);
        copyButton.setEnabled(true);
        saveButton.setEnabled(true);
    }

    private void tick() {
        Job j = job;
        if (j == null)
            return;
        String phase = j.phase();
        long total = j.total();
        long done = j.done();
        long now = System.nanoTime();

        if (!phase.equals(lastPhase)) {
            lastPhase = phase;
            lastDone = 0;
            lastNanos = now;
            rate = 0;
        } else if (now > lastNanos) {
            double inst = (done - lastDone) / ((now - lastNanos) / 1e9);
            if (inst >= 0 && done > lastDone)
                rate = rate == 0 ? inst : rate * 0.7 + inst * 0.3;
            lastDone = done;
            lastNanos = now;
        }

        phaseLabel.setText(phaseName(phase) + (total > 0 ? String.format(": %,d из %,d", done, total) : "…"));
        if (total > 0) {
            bar.setIndeterminate(false);
            bar.setValue((int) (done * 1000 / total));
            bar.setString(String.format("%.1f%%", done * 100D / total));
            if (rate > 0 && done < total) {
                double left = (total - done) / rate;
                timeLabel.setText(String.format("Прошло %s · осталось ≈ %s · закончит около %s", duration(j.totalSeconds()), duration(left),
                    new SimpleDateFormat("HH:mm:ss").format(new Date(System.currentTimeMillis() + (long) (left * 1000)))));
            } else
                timeLabel.setText("Прошло " + duration(j.totalSeconds()) + (done == 0 ? " · оцениваю время…" : ""));
        } else {
            bar.setIndeterminate(true);
            bar.setString("");
            timeLabel.setText("Прошло " + duration(j.totalSeconds()));
        }
    }

    private static String phaseName(String phase) {
        switch (phase) {
            case "Reading model":
                return "Чтение модели";
            case "Loading textures":
                return "Загрузка текстур";
            case "Converting triangles":
                return "Конвертация треугольников";
            case "Merging":
                return "Объединение результатов";
            case "Writing":
                return "Формирование текста";
            default:
                return phase;
        }
    }

    static String duration(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        if (s >= 3600)
            return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String translate(String message) {
        if (message == null)
            return "Неизвестная ошибка";
        if (message.startsWith("Cannot read model file"))
            return "Не удаётся прочитать файл модели. Он существует?";
        if (message.startsWith("The model is too small"))
            return "Модель слишком мелкая для этих настроек, ничего не получилось. Увеличьте макс. размер или сетку.";
        if (message.startsWith("The model is too big for this grid"))
            return "Модель слишком большая для этой сетки. Уменьшите макс. размер.";
        if (message.startsWith("Empty texture field"))
            return "Выберите файл текстуры.";
        return message;
    }

    private void copy() {
        if (resultText == null)
            return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(resultText), null);
            copyButton.setText("Скопировано ✓");
            Timer back = new Timer(1500, e -> copyButton.setText("Копировать в буфер обмена"));
            back.setRepeats(false);
            back.start();
        } catch (Exception | OutOfMemoryError e) {
            error("Не удалось скопировать (слишком большой текст?). Используйте «Сохранить в .txt».");
        }
    }

    private void save() {
        if (resultText == null)
            return;
        JFileChooser chooser = new JFileChooser(prefs.get("dir", null));
        String base = new File(modelField.getText()).getName().replaceAll("\\.[^.]*$", "");
        chooser.setSelectedFile(new File(base + ".txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), resultText.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                error("Не удалось сохранить: " + e.getMessage());
            }
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private File choose(String title, FileNameExtensionFilter filter, String current) {
        String dir = prefs.get("dir", null);
        File cur = current == null || current.isBlank() ? null : new File(current.replace("\"", ""));
        JFileChooser chooser = new JFileChooser(cur != null && cur.getParentFile() != null ? cur.getParentFile() : dir == null ? null : new File(dir));
        chooser.setDialogTitle(title);
        chooser.setFileFilter(filter);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private static JPanel titled(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field, JComponent extra) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = field instanceof JTextField || field instanceof JSlider || field instanceof JProgressBar || field instanceof JScrollPane ? GridBagConstraints.HORIZONTAL
                : GridBagConstraints.NONE;
        if (extra == null)
            c.gridwidth = 2;
        panel.add(field, c);
        if (extra != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            panel.add(extra, c);
        }
    }
}
