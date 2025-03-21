package ij3d.shortcuts;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.DefaultCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

@SuppressWarnings("serial")
public class ShortCutTable extends JTable {

	public ShortCutTable(ShortCuts shortcuts) {
		super(new ShortcutTableModel(shortcuts));
		setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
		setDefaultEditor(String.class, new ShortcutTableEditor());
	}

	private static final class ShortcutTableEditor extends DefaultCellEditor {
		ShortcutTableEditor() {
			super(getEditingTextField());
		}

		public static JTextField getEditingTextField() {
			final JTextField tf = new JTextField();
			tf.addKeyListener(new KeyAdapter() {
				
				public void keyPressed(KeyEvent e) {
					if(e.getKeyCode() != KeyEvent.VK_ENTER) {
						String stroke = KeyStroke.getKeyStrokeForEvent(e).toString();
						((JTextField)e.getComponent()).setText(stroke);
						e.consume();
					}
				}

				
				public void keyTyped(KeyEvent e) {
					e.consume();
				}

				
				public void keyReleased(KeyEvent e) {
					e.consume();
				}
			});
			return tf;
		}
	}

	private static final class ShortcutTableModel implements TableModel {

		final ShortCuts shortcuts;

		public ShortcutTableModel(ShortCuts shortcuts) {
			this.shortcuts = shortcuts;
		}

		
		public void addTableModelListener(TableModelListener l) {}
		
		public void removeTableModelListener(TableModelListener l) {}

		
		public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}

		
		public int getColumnCount() {
			return 2;
		}

		
		public String getColumnName(int col) {
			return col == 0 ? "Command" : "Shortcut";
		}

		
		public int getRowCount() {
			return shortcuts.getNumberOfCommands();
		}

		
		public Object getValueAt(int row, int col) {
			String command = shortcuts.getCommand(row);
			return col == 0 ? command : shortcuts.getShortCut(command);
		}

		
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnIndex == 1;
		}

		
		public void setValueAt(Object aValue, int row, int col) {
			if(col != 1)
				return;
			String command = shortcuts.getCommand(row);
			shortcuts.setShortCut(command, (String)aValue);
		}
	}
}

