package ij3d.contextmenu;

import ij3d.Content;
import ij3d.ContentConstants;
import ij3d.IJ3dExecuter;
import ij3d.Image3DUniverse;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class ContextMenu implements ActionListener, ItemListener, ContentConstants {

	private JPopupMenu popup = new JPopupMenu();

	private Image3DUniverse univ;
	private IJ3dExecuter iJ3dExecuter;

	private Content content;

	private JMenuItem slices, updateVol, fill, smoothMesh, smoothAllMeshes, smoothDialog, colorSurface, decimateMesh;
	private JCheckBoxMenuItem shaded, saturated;

	public ContextMenu (Image3DUniverse univ) {

		this.univ = univ;
		this.iJ3dExecuter = univ.getExecuter();

		slices = new JMenuItem("Adjust slices");
		slices.addActionListener(this);
		popup.add(slices);

		updateVol = new JMenuItem("Update Volume");
		updateVol.addActionListener(this);
		popup.add(updateVol);

		fill = new JMenuItem("Fill selection");
		fill.addActionListener(this);
		popup.add(fill);

		JMenu smooth = new JMenu("Smooth");
		popup.add(smooth);

		smoothMesh = new JMenuItem("Smooth mesh");
		smoothMesh.addActionListener(this);
		smooth.add(smoothMesh);

		smoothAllMeshes = new JMenuItem("Smooth all meshes");
		smoothAllMeshes.addActionListener(this);
		smooth.add(smoothAllMeshes);

		decimateMesh = new JMenuItem("Decimate mesh");
		decimateMesh.addActionListener(this);
		popup.add(decimateMesh);

		smoothDialog = new JMenuItem("Smooth control");
		smoothDialog.addActionListener(this);
		smooth.add(smoothDialog);

		shaded = new JCheckBoxMenuItem("Shade surface");
		shaded.setState(true);
		shaded.addItemListener(this);
		popup.add(shaded);

		saturated = new JCheckBoxMenuItem("Saturated volume rendering");
		saturated.setState(false);
		saturated.addItemListener(this);
		popup.add(saturated);

		colorSurface = new JMenuItem("Color surface from image");
		colorSurface.addActionListener(this);
		popup.add(colorSurface);

	}

	public void showPopup(MouseEvent e) {
		content = univ.getPicker().getPickedContent(e.getX(), e.getY());
		if(content == null)
			return;
		univ.select(content);
		shaded.setState(content.isShaded());
		saturated.setState(content.isSaturatedVolumeRendering());
		if(popup.isPopupTrigger(e))
			popup.show(e.getComponent(), e.getX(), e.getY());
	}

	
	public void itemStateChanged(ItemEvent e) {
		Object src = e.getSource();
		if(src == shaded)
			iJ3dExecuter.setShaded(content, shaded.getState());
		else if(src == saturated)
			iJ3dExecuter.setSaturatedVolumeRendering(content, saturated.getState());
	}

	
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == updateVol)
			iJ3dExecuter.updateVolume(content);
		else if (src == slices)
			iJ3dExecuter.changeSlices(content);
		else if (src == fill)
			iJ3dExecuter.fill(content);
		else if (src == smoothMesh)
			iJ3dExecuter.smoothMesh(content);
		else if (src == smoothAllMeshes)
			iJ3dExecuter.smoothAllMeshes();
		else if (src == smoothDialog)
			iJ3dExecuter.smoothControl();
		else if (src == decimateMesh)
			iJ3dExecuter.decimateMesh();
		else if(src == colorSurface)
			iJ3dExecuter.applySurfaceColors(content);
	}
}
