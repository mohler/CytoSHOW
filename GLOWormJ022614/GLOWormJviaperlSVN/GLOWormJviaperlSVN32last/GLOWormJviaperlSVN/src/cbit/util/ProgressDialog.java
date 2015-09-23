package cbit.util;
import java.awt.*;
import javax.swing.*;
/**
 * Insert the type's description here.
 * Creation date: (5/18/2004 1:14:29 AM)
 * @author: Ion Moraru
 */
public class ProgressDialog extends JDialog {
	private JPanel ivjJDialogContentPane = null;
	private JLabel ivjJLabel1 = null;
	private JProgressBar ivjJProgressBar1 = null;
	private GridLayout ivjJDialogContentPaneGridLayout = null;

public ProgressDialog() {
	super();
	initialize();
}

/**
 * Insert the method's description here.
 * Creation date: (5/19/2004 6:08:36 PM)
 * @param owner java.awt.Frame
 */
public ProgressDialog(Frame owner) {
	super(owner);
	initialize();
}


/**
 * Return the JDialogContentPane property value.
 * @return javax.swing.JPanel
 */
/* WARNING: THIS METHOD WILL BE REGENERATED. */
private javax.swing.JPanel getJDialogContentPane() {
	if (ivjJDialogContentPane == null) {
		try {
			ivjJDialogContentPane = new javax.swing.JPanel();
			ivjJDialogContentPane.setName("JDialogContentPane");
			ivjJDialogContentPane.setLayout(getJDialogContentPaneGridLayout());
			getJDialogContentPane().add(getJLabel1(), getJLabel1().getName());
			getJDialogContentPane().add(getJProgressBar1(), getJProgressBar1().getName());
			// user code begin {1}
			// user code end
		} catch (java.lang.Throwable ivjExc) {
			// user code begin {2}
			// user code end
			handleException(ivjExc);
		}
	}
	return ivjJDialogContentPane;
}


/**
 * Return the JDialogContentPaneGridLayout property value.
 * @return java.awt.GridLayout
 */
/* WARNING: THIS METHOD WILL BE REGENERATED. */
private java.awt.GridLayout getJDialogContentPaneGridLayout() {
	java.awt.GridLayout ivjJDialogContentPaneGridLayout = null;
	try {
		/* Create part */
		ivjJDialogContentPaneGridLayout = new java.awt.GridLayout(2, 1);
	} catch (java.lang.Throwable ivjExc) {
		handleException(ivjExc);
	};
	return ivjJDialogContentPaneGridLayout;
}


/**
 * Return the JLabel1 property value.
 * @return javax.swing.JLabel
 */
/* WARNING: THIS METHOD WILL BE REGENERATED. */
private javax.swing.JLabel getJLabel1() {
	if (ivjJLabel1 == null) {
		try {
			ivjJLabel1 = new javax.swing.JLabel();
			ivjJLabel1.setName("JLabel1");
			ivjJLabel1.setText("We are doing something...");
			ivjJLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			// user code begin {1}
			// user code end
		} catch (java.lang.Throwable ivjExc) {
			// user code begin {2}
			// user code end
			handleException(ivjExc);
		}
	}
	return ivjJLabel1;
}

/**
 * Return the JProgressBar1 property value.
 * @return javax.swing.JProgressBar
 */
/* WARNING: THIS METHOD WILL BE REGENERATED. */
private javax.swing.JProgressBar getJProgressBar1() {
	if (ivjJProgressBar1 == null) {
		try {
			ivjJProgressBar1 = new javax.swing.JProgressBar();
			ivjJProgressBar1.setName("JProgressBar1");
			ivjJProgressBar1.setStringPainted(true);
			// user code begin {1}
			// user code end
		} catch (java.lang.Throwable ivjExc) {
			// user code begin {2}
			// user code end
			handleException(ivjExc);
		}
	}
	return ivjJProgressBar1;
}

/**
 * Called whenever the part throws an exception.
 * @param exception java.lang.Throwable
 */
private void handleException(java.lang.Throwable exception) {

	/* Uncomment the following lines to print uncaught exceptions to stdout */
	System.out.println("--------- UNCAUGHT EXCEPTION ---------");
	exception.printStackTrace(System.out);
}


/**
 * Initialize the class.
 */
/* WARNING: THIS METHOD WILL BE REGENERATED. */
private void initialize() {
	try {
		// user code begin {1}
		// user code end
		setName("DocumentLoader");
		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		setSize(341, 88);
		setTitle("Please wait:");
		setContentPane(getJDialogContentPane());
	} catch (java.lang.Throwable ivjExc) {
		handleException(ivjExc);
	}
	// user code begin {2}
	// user code end
}

/**
 * main entrypoint - starts the part when it is run as an application
 * @param args java.lang.String[]
 */
public static void main(java.lang.String[] args) {
	try {
		ProgressDialog aProgressDialog;
		aProgressDialog = new ProgressDialog();
		aProgressDialog.setModal(true);
		aProgressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent e) {
				System.exit(0);
			};
		});
		aProgressDialog.show();
		java.awt.Insets insets = aProgressDialog.getInsets();
		aProgressDialog.setSize(aProgressDialog.getWidth() + insets.left + insets.right, aProgressDialog.getHeight() + insets.top + insets.bottom);
		aProgressDialog.setVisible(true);
	} catch (Throwable exception) {
		System.err.println("Exception occurred in main() of javax.swing.JDialog");
		exception.printStackTrace(System.out);
	}
}


/**
 * Insert the method's description here.
 * Creation date: (5/19/2004 1:06:46 PM)
 * @param message java.lang.String
 */
void setMessage(String message) {
	getJLabel1().setText(message);
}


/**
 * Insert the method's description here.
 * Creation date: (5/19/2004 1:06:18 PM)
 * @param progress int
 */
void setProgress(int progress) {
	getJProgressBar1().setValue(progress);
}


/**
 * Insert the method's description here.
 * Creation date: (5/19/2004 1:06:18 PM)
 * @param progress int
 */
void setProgressBarString(String progressString) {
	getJProgressBar1().setString(progressString);
}
}