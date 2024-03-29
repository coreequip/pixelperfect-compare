package dev.coreequip.visualcompare;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class App extends JFrame implements ActionListener, MouseListener, MouseMotionListener {

    private Point relLoc;
    private final JLabel label;
    private final JButton toggleButtonAot;
    private Image currentImage = null;
    private boolean backgroundRemoved = false;
    private boolean alwaysOnTop = false;


    static Image getImageFromClipboard() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return (Image) transferable.getTransferData(DataFlavor.imageFlavor);
            }
        } catch (UnsupportedFlavorException | IOException e) {
            return null;
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            String version = App.class.getPackage().getImplementationVersion();
            String appTitle = "PixelPerfect Compare v" + (version == null ? "0.DEV" : version);
            System.setProperty("sun.java2d.uiScale", "1.0");
            System.setProperty("apple.awt.application.name", appTitle);
            System.setProperty("apple.awt.application.appearance", "system");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            new App(appTitle);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public App(String appTitle) throws HeadlessException, IOException {
        final boolean isMacOs = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        final JToolBar jtb = new JToolBar(SwingConstants.VERTICAL);
        setLayout(new BorderLayout());
        add(jtb, BorderLayout.EAST);
        jtb.setFloatable(false);

        addMouseListener(this);
        addMouseMotionListener(this);
        jtb.addMouseListener(this);
        jtb.addMouseMotionListener(this);

        jtb.add(makeToolbarButton("clipboard", "Paste clipboard image", "paste"));
        jtb.add(makeToolbarButton("minus-circle", "Decrease opacity", "dec"));
        jtb.add(makeToolbarButton("plus-circle", "Increase opacity", "inc"));
        jtb.add(makeToolbarButton("magic", "Remove background", "remove-bg"));
        toggleButtonAot = makeToolbarButton("pushpin-off", "Toggle always on top", "toggle-aot");
        jtb.add(toggleButtonAot);
        jtb.add(makeToolbarButton("times", "Exit", "exit"));

        final String cmds = "move-left|alt LEFT,move-right|alt RIGHT,move-top|alt UP,move-bottom|alt DOWN," +
                "move-left-fast|alt shift LEFT,move-right-fast|alt shift RIGHT," +
                "move-top-fast|alt shift UP,move-bottom-fast|alt shift DOWN," +
                "inc|control I,dec|control D,paste|control V,exit|control Q,remove-bg|control B,toggle-aot|control T";
        for (String cmdTupel : cmds.split(",")) {
            final String[] split = cmdTupel.split("\\|");
            getRootPane().registerKeyboardAction(
                    this,
                    split[0],
                    KeyStroke.getKeyStroke(split[1]),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        }


        label = new JLabel(getIcon("logo"));

        add(label, BorderLayout.CENTER);

        setTitle(appTitle);

        setIconImage(getIcon("icon").getImage());
        if (isMacOs) {
            Taskbar.getTaskbar().setIconImage(getIcon("icon").getImage());
        }


        setMinimumSize(new Dimension(200, 260));
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JButton makeToolbarButton(String iconName, String toolTipText, String command) throws IOException {
        JButton button = new JButton(getIcon(iconName));
        button.addActionListener(this);
        button.setActionCommand(command);
        button.setToolTipText(toolTipText);
        button.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        return button;
    }

    private ImageIcon getIcon(String iconName) throws IOException {
        return new ImageIcon(ImageIO.read(Objects.requireNonNull(App.class.getResourceAsStream("/img/" + iconName + ".png"))));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        float opaque = getOpacity();
        switch (e.getActionCommand()) {
            case "paste":
                currentImage = getImageFromClipboard();
                if (null == currentImage) return;

                label.setIcon(new ImageIcon(currentImage));
                backgroundRemoved = false;
                label.setMinimumSize(new Dimension(currentImage.getWidth(this), currentImage.getHeight(this)));
                pack();
                setLocationRelativeTo(null);
                break;
            case "remove-bg":
                boolean strictRemoval = (e.getModifiers() & ActionEvent.ALT_MASK) > 0;
                if (currentImage == null) return;
                if (backgroundRemoved) {
                    label.setIcon(new ImageIcon(currentImage));
                    backgroundRemoved = false;
                    return;
                }
                backgroundRemoved = true;
                int width = currentImage.getWidth(this);
                int height = currentImage.getHeight(this);
                final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                img.getGraphics().drawImage(currentImage, 0, 0, this);
                HashMap<Integer, Integer> colorCount = new HashMap<>(4);
                int[][] coords = new int[][]{{0, 0}, {width - 1, 0}, {0, height - 1}, {width - 1, height - 1}};
                for (int[] xy : coords) {
                    int rgb = img.getRGB(xy[0], xy[1]) & 0x00FFFFFF;
                    colorCount.put(rgb, null != colorCount.get(rgb) ? colorCount.get(rgb) + 1 : 1);
                }
                int maxCnt = 0, rgb = 0;
                for (Map.Entry<Integer, Integer> entry : colorCount.entrySet()) {
                    if (entry.getValue() <= maxCnt) continue;
                    maxCnt = entry.getValue();
                    rgb = entry.getKey();
                }
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        int cRGB = img.getRGB(x, y) & 0x00FFFFFF;
                        if (strictRemoval && cRGB != rgb) continue;
                        if (!strictRemoval && !isRgbSimilar(cRGB, rgb)) continue;
                        img.setRGB(x, y, rgb & 0xFF000000);
                    }
                label.setIcon(new ImageIcon(img));
                break;
            case "dec":
                setOpacity(opaque > .2f ? opaque - .1f : opaque);
                break;
            case "inc":
                setOpacity(opaque < 1 ? opaque + .1f : opaque);
                break;
            case "exit":
                dispose();
                break;
            case "move-left":
                moveWindowRelative(-1, 0);
                break;
            case "move-right":
                moveWindowRelative(1, 0);
                break;
            case "move-top":
                moveWindowRelative(0, -1);
                break;
            case "move-bottom":
                moveWindowRelative(0, 1);
                break;
            case "move-left-fast":
                moveWindowRelative(-20, 0);
                break;
            case "move-right-fast":
                moveWindowRelative(20, 0);
                break;
            case "move-top-fast":
                moveWindowRelative(0, -20);
                break;
            case "move-bottom-fast":
                moveWindowRelative(0, 20);
                break;
            case "toggle-aot":
                this.alwaysOnTop ^= true;
                this.setAlwaysOnTop(this.alwaysOnTop);
                try {
                    this.toggleButtonAot.setIcon(this.getIcon("pushpin-" + (this.alwaysOnTop ? "on" : "off")));
                } catch (IOException ignored) {
                }
                break;
        }
    }

    private static boolean isRgbSimilar(int actual, int reference) {
        final int EPSILON = 5;
        return (actual >> 16 & 0xFF) > (reference >> 16 & 0xFF) - EPSILON &&
                (actual >> 16 & 0xFF) < (reference >> 16 & 0xFF) + EPSILON &&
                (actual >> 8 & 0xFF) > (reference >> 8 & 0xFF) - EPSILON &&
                (actual >> 8 & 0xFF) < (reference >> 8 & 0xFF) + EPSILON &&
                (actual & 0xFF) > (reference & 0xFF) - EPSILON &&
                (actual & 0xFF) < (reference & 0xFF) + EPSILON;
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        relLoc = new Point(getLocationOnScreen().x - e.getLocationOnScreen().x,
                getLocationOnScreen().y - e.getLocationOnScreen().y);
    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {
        setLocation(new Point(
                relLoc.x + e.getLocationOnScreen().x,
                relLoc.y + e.getLocationOnScreen().y
        ));
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    private void moveWindowRelative(int x, int y) {
        setLocation(getLocation().x + x, getLocation().y + y);
    }

}
