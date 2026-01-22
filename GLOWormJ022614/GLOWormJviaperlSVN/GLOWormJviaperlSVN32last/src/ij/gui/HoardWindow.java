package ij.gui;

import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.Component;
import java.awt.Scrollbar;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

public class HoardWindow extends StackWindow {

    public HoardWindow(ImagePlus imp) {
        // Preserving your custom signature exactly:
        super(imp, null, false);
        
        // 1. NEUTER SCROLLBARS (Focus Fix)
        disableScrollbarFocus(this);
        
        // 2. MAGNET FOCUS (Start-up Fix)
        this.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (ic != null) ic.requestFocus();
            }
            @Override
            public void windowLostFocus(WindowEvent e) { }
        });

        // 3. NAVIGATION LOGIC
        if (ic != null) {
            // A. Restore Arrow Keys (Custom Handler)
            ic.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    handleNavigationKeys(e);
                }
            });

            // B. Explicit Dimension Mapping (Wheel Fix)
            // Fixes "Shift+Scroll" failing on Windows
            ic.addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    handleExplicitWheel(e);
                }
            });
        }
    }

    /**
     * Enforces strict dimension mapping:
     * Unmod = Z (Slice) - Handled naturally by ImageJ if we don't consume
     * Shift = T (Frame)
     * Alt   = C (Channel)
     */
    private void handleExplicitWheel(MouseWheelEvent e) {
        int rotation = e.getWheelRotation();
        if (rotation == 0) return;

        // SHIFT = TIME (Frame)
        if (e.isShiftDown()) {
            int currentT = imp.getFrame();
            int maxT = imp.getNFrames();
            // Invert logic: Wheel down (positive) usually means "Next", so +rotation
            int newT = clamp(currentT + rotation, 1, maxT);
            
            if (newT != currentT) {
                imp.setPosition(imp.getChannel(), imp.getSlice(), newT);
            }
            // CRITICAL: Consume event so Windows doesn't try to Horizontal Scroll
            e.consume(); 
        } 
        // ALT = CHANNEL
        else if (e.isAltDown()) {
            int currentC = imp.getChannel();
            int maxC = imp.getNChannels();
            int newC = clamp(currentC + rotation, 1, maxC);
            
            if (newC != currentC) {
                imp.setPosition(newC, imp.getSlice(), imp.getFrame());
            }
            e.consume();
        } 
        // UNMODIFIED = SLICE (Z)
        // We do NOT consume here. We let the event bubble up so ImageJ's 
        // standard Slice scrolling (which works fine) handles it.
    }

    private int clamp(int val, int min, int max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
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