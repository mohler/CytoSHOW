package ij.gui;


import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.Component;
import java.awt.Scrollbar;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

public class HoardWindow extends StackWindow {

    public HoardWindow(ImagePlus imp) {
        super(imp, null, false);
        
        // 1. The "Neutering": Prevent scrollbars from stealing focus.
        // This ensures the Canvas keeps focus even after you click/drag a slider.
        disableScrollbarFocus(this);
        
        // 2. The "Magnet": Force focus to the Canvas whenever the window is active.
        // This fixes the "Dead on Arrival" bug where keys don't work until you click.
        this.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (ic != null) ic.requestFocus();
            }
            @Override
            public void windowLostFocus(WindowEvent e) { }
        });

        // 3. The Navigation Logic
        // Since we disabled the scrollbars, they no longer handle Arrow Keys natively.
        // We must re-implement that behavior on the Canvas.
        if (ic != null) {
            ic.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    handleNavigationKeys(e);
                }
            });
        }
    }

    /**
     * Recursively finds all AWT Scrollbars and makes them non-focusable.
     */
    private void disableScrollbarFocus(Component c) {
        if (c instanceof Scrollbar) {
            c.setFocusable(false);
        } else if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container)c).getComponents()) {
                disableScrollbarFocus(child);
            }
        }
    }

    /**
     * Handles Arrow Keys for scrubbing.
     * Note: We respect ROIs. If an ROI is active, we let ImageJ handle the keys (nudge).
     * If NO ROI is active, we scrub.
     */
    private void handleNavigationKeys(KeyEvent e) {
        // Ignore if modifier keys are down (Ctrl/Shift/Alt often used for Zoom/Fast)
        if (e.isControlDown() || e.isShiftDown() || e.isAltDown()) return;

        // 1. If an ROI is drawn, let standard ImageJ behavior (Nudge) take precedence.
        if (imp.getRoi() != null) return;

        int code = e.getKeyCode();
        
        // 2. Determine which axis to move. 
        // Priority: Time (Frame) > Z (Slice).
        boolean isTime = imp.getNFrames() > 1;
        
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_DOWN) {
            // BACKWARD
            if (isTime) {
                if (imp.getFrame() > 1) imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame() - 1);
            } else {
                if (imp.getSlice() > 1) imp.setSlice(imp.getSlice() - 1);
            }
            e.consume(); // Mark as handled
            
        } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_UP) {
            // FORWARD
            if (isTime) {
                if (imp.getFrame() < imp.getNFrames()) imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame() + 1);
            } else {
                if (imp.getStackSize() > imp.getSlice()) imp.setSlice(imp.getSlice() + 1);
            }
            e.consume();
        }
    }
}