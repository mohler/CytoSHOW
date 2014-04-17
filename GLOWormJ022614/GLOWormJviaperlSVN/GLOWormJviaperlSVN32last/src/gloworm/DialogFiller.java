package gloworm;

import ij.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import java.lang.Class;
import javax.swing.text.*;
import javax.swing.event.*;
import java.lang.reflect.Array;

public class DialogFiller {
    /*Fills in dialog boxes with a particular title (by manipulating
      standard AWT widgets and some SWING widgets with similar
      functionality) for the user.  Useful for automating repetitive
      calls to plugins that insist on putting up a dialog box every
      time they run.

      Responses to the dialog box are given to the constructor as
      arrays, each containing the states that the box's instances of
      one standard AWT widget class will be set to (if enabled).
      Supported SWING widgets are treated as whatever AWT widgets they
      behave most like; see the array declarations below for
      information about which AWT widgets these are and how their
      states are represented.  Note that a disabled widget still "uses
      up" one entry in the state array for its type.

      WARNING: Elements of the widget-state arrays are not checked for
      validity before being used to fill the dialog.  Invalid values
      will be used anyway regardless of the dialog's attempts to
      reject them; this will cause the plugin to (at best) sit
      uselessly while its dialog remains on screen for lack of decent
      input--or (at worst) to reshow the dialog, which will be filled
      again with the same bad input to face yet more rejection in a
      horrible beeping infinite loop.  Code instantiating a
      DialogFiller should therefore check values to place in the state
      arrays carefully, especially if any of it was user input.*/

    String targetTitle; //title of dialog this DialogFiller fills
    private Boolean active=Boolean.FALSE;
	//let this DialogFiller respond to its target dialog?
    private IllegalArgumentException stateArrayException;
    /*non-null if attempt to fill a target dialog failed because of
      too little information in one of the following arrays*/
    /*---Arrays of info to fill the dialog's widgets with, in the order
      that Container.getComponents() enumerates them. Null elements,
      where possible, mean ignore the corresponding widget.---*/
    private boolean[] buttonStates; //false means ignore button
    private Boolean[] checkboxStates; /*incl. all radio buttons.
					True: make sure box checked;
					false: make sure box
					unchecked.*/
    private String[] textComponentStates; /*includes editable
                                            JComboBoxes and
                                            FileDialogs (element 0 for
                                            path, element 1 for
                                            filename).  Excludes SWING
                                            spinners--too new to be
                                            widely compatible.*/
    private int[][] listStates; /*1 int[] per List for indicating all
				  items to be selected in it.  The
				  other items all get deselected.*/
    private Integer[] choiceStates; //includes uneditable JComboBoxes
    private Integer[] scrollbarStates; /*includes SWING sliders.
					 Integer[] not int[] is used
					 to allow null elements
					 meaning the scrollbar can be
					 ignored (i.e. it's just for
					 getting around the UI)*/
    //not going to bother dealing with SWING JColorChoosers, JTrees, etc. yet
    
    private static Hashtable dialogFillers;
    private static DialogDetector dialogDetector;
    
    private static class DialogDetector implements Runnable, AWTEventListener {
	/*Intercepts any dialogs ImageJ produces and dispatches the
          appropriate DialogFiller to handle them.*/
	private Boolean active=Boolean.FALSE; //query ImageJ for dialogs?
	private Thread detectionLoop/* =new Thread(this)*/;
	private Vector usedDialogs=new Vector();
	/*Insert dialogs here after filling them to make sure
	  they're not filled again while this call to run() is in
	  progress--otherwise deadlock in detection loop and
	  confusion among dialog's listeners can result.*/
	
	private void setActive(boolean active) {
	    synchronized(this) {
		this.active=(active? Boolean.TRUE: Boolean.FALSE);
	    }
	}
	
	public boolean isActive() {
	    boolean returnValue;
	    synchronized(active) {
		returnValue=active.booleanValue();
	    }
	    return returnValue;
	}

	void rectifyState(boolean newDFState) {
	    /*Saves the computer & programmer some work by
	      ensuring dialog-detection loop runs iff any
	      DialogFiller is active.  Called when a DialogFiller
	      starts or stops being active.*/
	    if (newDFState) { //simple--we're given that a DF is active, now let the loop run
		if (!active.booleanValue()) {
		    setActive(true);
		    //detectionLoop=new Thread(this);
		    //detectionLoop.start();
		    this.run();
		}
	    } else if (active.booleanValue()) { //tricky--we have to see if *all* DFs are now inactive
		Enumeration dFs;
		dFs = DialogFiller.dialogFillers.elements();
		boolean keepDetecting=false;
		while (!keepDetecting && dFs.hasMoreElements())
		    keepDetecting=((DialogFiller) dFs.nextElement()).isActive();
		if (!keepDetecting) {
		    setActive(false);
		    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
		    //while (detectionLoop.isAlive()); //don't return before thread dies
		}
	    }
	}

	private Dialog firstFillableChildDialogOf(Window window) {
	    /*Seeks a dialog that's showing and not
              already found among window's descendents.  Returns first
              one it finds.*/
	    Window[] children=window.getOwnedWindows();
	    Dialog returnValue=null;
	    for (int index=0; children != null && index < children.length; index++) {
		if (children[index] instanceof Dialog &&
		    children[index].isShowing() &&
		    !usedDialogs.contains(children[index])) {
		    usedDialogs.add(children[index]);
		    returnValue=(Dialog) children[index];
		}
		else
		    returnValue=firstFillableChildDialogOf(children[index]);
		if (returnValue != null)
		    return returnValue;
	    }
	    //if we made it here there's no such dialog:
	    return null;
	}

	public void run() {
	    Toolkit.getDefaultToolkit().addAWTEventListener(this,
							    AWTEvent.COMPONENT_EVENT_MASK |
							    AWTEvent.WINDOW_EVENT_MASK);
	}

	public void eventDispatched(AWTEvent e) {
	    Dialog target;
	    DialogFiller targetFiller;
	    if (e instanceof ComponentEvent &&
		(e.getID()==ComponentEvent.COMPONENT_SHOWN ||
		 e.getID()==WindowEvent.WINDOW_OPENED) &&
		e.getSource() instanceof Dialog) {
		target=(Dialog) e.getSource();
		targetFiller=((DialogFiller) DialogFiller.dialogFillers.get(target.getTitle()));
		if (targetFiller != null)
		    synchronized(targetFiller) {
			try {
			    targetFiller.fill(target);
			} catch (IllegalArgumentException x) {
			    targetFiller.setActive(false);
			    targetFiller.stateArrayException=x;
			}
		    }
	    }
	}
	
	public void loopRun() {
	    //call this run again if proves useful
	    Window[] possibleDialogs;
	    Window currentWindow;
	    Dialog target;
	    DialogFiller targetFiller;
	    boolean localActive;
	    synchronized (active) {
		localActive=active.booleanValue();
		//unnecessary to synchronize whole loop on active, just the 1 access to it
	    }
	    while (localActive) { //false value should kill thread running this method
		//clear used status of dialogs no longer showing:
		for (int usedIndex=0; usedIndex < usedDialogs.size(); usedIndex++)
		    if (!((Window) usedDialogs.elementAt(usedIndex)).isShowing())
			usedDialogs.removeElementAt(usedIndex);
		target=firstFillableChildDialogOf(IJ.getInstance());
		//check among IJ's descendents
		if (target==null) {
		    //still could have missed descendents of image windows--check them:
		    int[] imageWindows=WindowManager.getIDList();
		    for (int index=0; index < imageWindows.length; index++)
			target=firstFillableChildDialogOf(WindowManager.getImage(imageWindows[index]).getWindow());
		}
		if (target != null) {
		    targetFiller=((DialogFiller) DialogFiller.dialogFillers.get(target.getTitle()));
		    synchronized(targetFiller) {
			if (targetFiller != null)
			    try {
				targetFiller.fill(target);
			    }
			    catch (IllegalArgumentException e) {
				targetFiller.setActive(false);
				targetFiller.stateArrayException=e;
				/*should kill the plugin but no
				  good way to do it from here, so
				  continue normal execution
				  (without the faulty filler) &
				  leave further response to
				  instantiating code*/
			    }
		    }
		}
		synchronized(active) {
		    localActive=active.booleanValue();
		}
		//Thread.yield(); //IJ will make it yield anyway
	    }
	}
    }
    
    DialogFiller(String targetTitle, boolean active, boolean[] buttonStates) {
	//For simple dialogs with just OK/Cancel/etc. at bottom.
	this(targetTitle, active, buttonStates, null, null, null, null, null);
    }
    DialogFiller(String targetTitle, boolean active, boolean[] buttonStates,
		 Boolean[] checkboxStates, Integer[] choiceStates, String textComponentStates[],
		 int[][] listStates, Integer[] scrollbarStates) {
	/*If your dialog doesn't have one of these categories of
          widget, put null instead of its states array.*/
	if (DialogFiller.dialogFillers==null) {
	    //first instantiation of this class, initialize static variables
	    DialogFiller.dialogFillers=new Hashtable();
	    DialogFiller.dialogDetector=new DialogDetector();
	    IJ.register(DialogFiller.class);
	}
	if (active)
	    this.setActive(true);
	else
	    this.active=(active?  Boolean.TRUE: Boolean.FALSE); //avoid unneeded call to DialogDetector.rectifyState()
	this.targetTitle=targetTitle;
	DialogFiller.register(targetTitle, this);
	setButtonStates(buttonStates);
	setCheckboxStates(checkboxStates);
	setChoiceStates(choiceStates);
	setTextComponentStates(textComponentStates);
	setListStates(listStates);
	setScrollbarStates(scrollbarStates);
    }
    private Object deepArrayClone(Object original) {
	/*Returns a recursive clone of original: elements of original
          are cloned (where possible) and subarrays of original are
          deepArrayCloned to create the array returned.*/
	int length;
	Object copy;
	Class elementType;
	if (original==null)
	    return null;
	synchronized(original) {
	    if ((elementType=original.getClass().getComponentType())==null)
		throw new ArrayStoreException("argument must be array or null");
	    length=Array.getLength(original);
	    copy=Array.newInstance(elementType, length);
	    for (int index=0; index < length; index++) {
		Object currentElement=Array.get(original, index);
		if (currentElement != null) {
		    Class currentElementClass=currentElement.getClass();
		    if (currentElementClass.getComponentType() != null) //currentElement is subarray--recurse
			Array.set(copy, index, deepArrayClone(currentElement));
		    else if (Cloneable.class.isAssignableFrom(currentElementClass))
			//replace copy[index]'s ref to original[index] with clone of it
			try {
			    Array.set(copy, index, currentElementClass.getMethod("clone", null).invoke(currentElement, null));
			} catch (Exception e) {
			    if (e instanceof RuntimeException)
				throw((RuntimeException) e);
			    Array.set(copy, index, currentElement); //put back ref we were trying to replace
			}
		    else //have to copy original[index] itself to component[index]
			Array.set(copy, index, Array.get(original, index));
		}
	    }
	}
	return copy;
    }
    private static void deregister(String targetTitle) {
	dialogFillers.remove(targetTitle);
    }
    public static void endUse() {
	/*Gets rid of *all* DialogFillers.  Useful when prematurely
          stopping a plugin, so old DialogFillers don't stick around
          to mess up the next run.*/
	dialogDetector.setActive(false);
	//make future calls to rectifyState() cheaper
	DialogFiller[] dfs=(DialogFiller[]) dialogFillers.values().toArray(new DialogFiller[0]);
	for (int index=0; index < dfs.length; index++)
	    dfs[index].selfDestruct();
    }
    private synchronized void fill(Container dialog) throws IllegalArgumentException {
	int buttonStateCounter=0, checkboxStateCounter=0, choiceStateCounter=0,
	    textComponentStateCounter=0, listStateCounter=0, scrollbarStateCounter=0;
	//synchronized(dialog) { //get rid of this?
	if (!this.isActive()) 
	    return;
	if (dialog instanceof FileDialog) {
	/*File dialogs' widgets are part of native window system not
          AWT & can't be manipulated by methods below, so use special
          method:*/
	    pickAFile((FileDialog) dialog);
	    return;
	}
	Component[] components=dialog.getComponents();
	for (int index=0; index < components.length; index++) {
	    //array boundary checking is done in method calls below
	    Class componentClass=components[index].getClass();
	    if (JToggleButton.class.isAssignableFrom(componentClass) ||  Checkbox.class.isAssignableFrom(componentClass))
		//checkbox or radio button
		tryChecking(components[index], componentClass, checkboxStateCounter++);
	    else if (AbstractButton.class.isAssignableFrom(componentClass) || Button.class.isAssignableFrom(componentClass))
		tryPushing(components[index], componentClass, buttonStateCounter++);
	    else if (JComboBox.class.isAssignableFrom(componentClass)) {
		if (((JComboBox) components[index]).isEditable()) //edit it
		    tryWriting(components[index], componentClass, textComponentStateCounter++);
		else //it functions like a Choice
		    tryChoosing(components[index], componentClass, choiceStateCounter++);
	    }
	    else if (Choice.class.isAssignableFrom(componentClass)) 
		tryChoosing(components[index], componentClass, choiceStateCounter++);
	    else if (JTextComponent.class.isAssignableFrom(componentClass) || TextComponent.class.isAssignableFrom(componentClass))
		tryWriting(components[index], componentClass, textComponentStateCounter++);
	    else if (java.awt.List.class.isAssignableFrom(componentClass) || JList.class.isAssignableFrom(componentClass))
		tryMultiChoosing(components[index], componentClass, listStateCounter++);
	    else if (Scrollbar.class.isAssignableFrom(componentClass) || JScrollBar.class.isAssignableFrom(componentClass)
		     || JSlider.class.isAssignableFrom(componentClass))
		tryAdjusting(components[index], componentClass, scrollbarStateCounter++);
	    else if (JFileChooser.class.isAssignableFrom(componentClass)) {
		j2PickAFile((JFileChooser) components[index]);
	    }
	    else if (Container.class.isAssignableFrom(componentClass))
		recurseContainer((Container) components[index]);
	    /*Necessary so the common practice of organizing widgets
              in Panels etc. doesn't shield them from fill() method.
              Side effect: Some JComponents that weren't covered above
              are containers of multiple manipulable widgets
              (e.g. JSpinner--text area with buttons), any of which
              can set the JComponent's state alone but all of which
              will have to be represented in the state array.  Use
              subclass DialogInspector to get an ordered list of
              widgets from the target dialog that will need to be
              accounted for.*/
	    else
		noteMiscellaneousWidget(components[index], componentClass);
	    //for benefit of DialogInspector subclass
	}
    }
    private static boolean fillerExistsFor(String targetTitle) {
	return dialogFillers.containsKey(targetTitle);
    }
    private void fireJComboBoxEvents(JComboBox jcb, int oldIndex, int stateCounter) {
	//workaround for difference between Java versions in dispatching of events from JComboBox
	if (System.getProperty("java.version").substring(0, 3).compareTo("1.3") <= 0) { //adapted from ij.IJ's code
	    /*Java < 1.4 is running and the setSelected method didn't fire the events itself--fix that:*/
	    if (oldIndex != -1)
		jcb.dispatchEvent(new ItemEvent(jcb,
						ItemEvent.ITEM_STATE_CHANGED,
						jcb.getItemAt(oldIndex),
						ItemEvent.DESELECTED)); //not done for some reason--fix if problematic
	    if ((jcb.isEditable() && textComponentStates[stateCounter] != null) || 
		(!jcb.isEditable() && choiceStates[stateCounter].intValue() != -1)) //new selection made
		jcb.dispatchEvent(new ItemEvent(jcb,
						ItemEvent.ITEM_STATE_CHANGED, jcb.getSelectedItem(),
						ItemEvent.SELECTED));
	    jcb.dispatchEvent(new ActionEvent(jcb, ActionEvent.ACTION_PERFORMED, jcb.getActionCommand()));
	}
    }
    /*---Accessor methods for state arrays:---*/

    public boolean[] getButtonStates() {
	return (boolean[]) deepArrayClone(buttonStates);
	//use clone to protect state arrays & their locks from code invoking accessor methods
    }
    public Boolean[] getCheckboxStates() {
	return (Boolean[]) deepArrayClone(checkboxStates);
    }
    public Integer[] getChoiceStates() {
	return (Integer[]) deepArrayClone(choiceStates);
    }
    public int[][] getListStates() {
	return (int[][]) deepArrayClone(listStates);
    }
    public Integer[] getScrollbarStates() {
	return (Integer[]) deepArrayClone(scrollbarStates);
    }
    public static boolean[] getStandardButtonStates() {
	/*Returns an array suitable for use as button states in a DialogFiller whose target has only OK and Cancel buttons.*/
	boolean b=IJ.isMacintosh();
	return new boolean[] {!b, b};
    }
    public IllegalArgumentException getStateArrayException() {
	/*Returns for convenience of instantiating code the exception,
          if any, that deactivated this DialogFiller when it tried to
          fill its target dialog with insufficient information in its
          state arrays.*/
	return stateArrayException;
    }
    public String[] getTextComponentStates() {
	return (String[]) deepArrayClone(textComponentStates);
    }
    public boolean isActive() {
	boolean returnValue;
	synchronized(this.active) {
	    returnValue=active.booleanValue();
	}
	return returnValue;
    }
    private void j2PickAFile(JFileChooser jfc) {
	if (textComponentStates==null || textComponentStates.length < 2)
	    throw new IllegalArgumentException("Need to specify filename & directory as text states for '"
					       + targetTitle + "' dialog.");
	else {
	    synchronized(jfc) {
		jfc.setSelectedFile(new java.io.File(textComponentStates[0] + textComponentStates[1]));
		jfc.approveSelection();
	    }
	}
    }
    private void noteMiscellaneousWidget(Component widget, Class type) {}
    private void pickAFile(FileDialog fd) {
	/*FileDialogs aren't made of AWT widgets--fill them by means
          Java does provide, using 1st item in text-component states
          array as directory name and 2nd as filename.*/
	if (textComponentStates==null || textComponentStates.length < 2)
	    throw new IllegalArgumentException("Need to specify directory & filename as text states for '" + targetTitle + "' dialog.");
	else {
	    synchronized(fd) {
		fd.setDirectory(textComponentStates[0]);
		fd.setFile(textComponentStates[1]);
		IJ.wait(250); //otherwise fd may never go away: bug?
		fd.dispose(); //equivalent to Cancel (if filename/directory are null) or OK (if they aren't)
		for (int i=0; i < 1000000; i++) {
		    String ignoreThisFile=fd.getFile();
		 }
		/*Now any ij.io.SaveDialog that might
		  have created fd won't be first to call fd.getFile()
		  after disposal.  This magically prevents a bug where
		  the SaveDialog catches fd off-guard somehow with the
		  method call, gets null back and tells the world fd
		  was canceled.*/
	    }
	}
    }
    void recurseContainer(Container container) throws IllegalArgumentException {
	fill(container);
    }
    private static void register(String targetTitle, DialogFiller dialogFiller) throws RuntimeException {
	    if (fillerExistsFor(targetTitle))
		throw new RuntimeException("Already have a DialogFiller for '" + targetTitle + "' box!");
	    else
		dialogFillers.put(targetTitle, dialogFiller);
    }
    public void selfDestruct() {
	/*Always remove unwanted DialogFillers this way!  Otherwise
          they'll stay in the hashtable and never get
          garbage-collected.*/
	this.setActive(false); //stop dialogDetector if this was last one active
	DialogFiller.deregister(targetTitle);
    }
    public void setActive(boolean active) {
	/*Activate or inactivate this DialogFiller (if its state
          arrays aren't known to be defective).*/
	if (active == this.active.booleanValue() || stateArrayException != null)
	    return; //what follows is expensive & shouldn't run for nothing
	synchronized(this) {
	    this.active=(active?  Boolean.TRUE: Boolean.FALSE);
	}
	DialogFiller.dialogDetector.rectifyState(active);
    }
    public synchronized void setButtonStates(boolean[] newStates) {
	this.buttonStates=(boolean[]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("button") == -1)
	    //the new state array should fix the problem, so:
	    stateArrayException=null;
    }
    public synchronized void setCheckboxStates(Boolean[] newStates) {
	this.checkboxStates=(Boolean[]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("checkbox") == -1)
	    stateArrayException=null;
    }
    public synchronized void setChoiceStates(Integer[] newStates) {
	this.choiceStates=(Integer[]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("choice") == -1)
	    stateArrayException=null;
    }
    public synchronized void setListStates(int[][] newStates) {
	this.listStates=(int[][]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("checkbox") == -1)
	    stateArrayException=null;
    }
    public synchronized void setScrollbarStates(Integer[] newStates) {
	this.scrollbarStates=(Integer[]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("checkbox") == -1)
	    stateArrayException=null;
    }
    public synchronized void setTargetTitle(String targetTitle) {
	/*Useful if dialog to be filled will vary its title
	  predictably over repeated appearances.  Pointless otherwise.*/
	if (!DialogFiller.fillerExistsFor(targetTitle)) {
	    //then the new targetTitle will be accepted--safe to discard old one
	    DialogFiller.deregister(this.targetTitle);
	    this.targetTitle=targetTitle;
	}
	DialogFiller.register(targetTitle, this);	
    }
    public synchronized void setTextComponentStates(String[] newStates) {
	this.textComponentStates=(String[]) deepArrayClone(newStates);
	if (stateArrayException != null &&
	    stateArrayException.getMessage().indexOf("text") == -1)
	    stateArrayException=null;
    }
    private void tryAdjusting(Component scrollbar, Class type, int scrollbarStateCounter) 
	throws IllegalArgumentException {
	if (scrollbarStates != null) {
	    if (scrollbarStateCounter >= scrollbarStates.length)
		throw new IllegalArgumentException("Need to specify more scrollbar states for '"
						   + targetTitle + "' dialog.");
	    else if (!scrollbar.isEnabled())
		return;
	    else if (scrollbarStates[scrollbarStateCounter] != null) {
		if (Scrollbar.class.isAssignableFrom(type) || JScrollBar.class.isAssignableFrom(type)) {
		    if (((Adjustable) scrollbar).getValue() != scrollbarStates[scrollbarStateCounter].intValue()) {
			((Adjustable) scrollbar).setValue(scrollbarStates[scrollbarStateCounter].intValue());
			scrollbar.dispatchEvent(new AdjustmentEvent((Adjustable) scrollbar,
								    AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
								    AdjustmentEvent.TRACK,
								    scrollbarStates[scrollbarStateCounter].intValue()));
			/*event simulates scrollbar "knob" being dragged
			  very very quickly to new location*/
		    }
		}
		else {
		    if (((JSlider) scrollbar).getValue() != scrollbarStates[scrollbarStateCounter].intValue())
			((JSlider) scrollbar).setValue(scrollbarStates[scrollbarStateCounter].intValue());
		    //fires same event that sliding slider with a single keystroke would
		}
	    }
	}
	else if (JSlider.class.isAssignableFrom(type))
	    throw new IllegalArgumentException("Need to specify more scrollbar states for '"
					       + targetTitle + "' dialog.");
	/*doesn't apply to Scrollbar or JScrollBar because dialogs
          often use these exclusively for navigating the UI, not for
          taking user input*/
    }
    private void tryChecking(Component checkbox, Class type, int checkboxStateCounter) 
	throws IllegalArgumentException {
	/*Ignores checkbox or makes sure its state is as specified in
	  checkboxStates.  Fires an ItemEvent on changing the
	  checkbox's state.*/
	if (checkboxStates == null || checkboxStateCounter >= checkboxStates.length)
	    throw new IllegalArgumentException("Need to specify more checkbox states for '"
						     + targetTitle + "' dialog.");
	else if (!checkbox.isEnabled())
	    return;
	else if  (checkboxStates[checkboxStateCounter] != null) {
	    if (JToggleButton.class.isAssignableFrom(type)) {
		while (checkboxStates[checkboxStateCounter].booleanValue()  != ((JToggleButton) checkbox).isSelected()) {
				/*checkbox needs state changed. Done
                                  inside while loop as workaround for
                                  Java bug (?) that makes doClick()
                                  sometimes fail.*/
		    ((JToggleButton) checkbox).doClick(0); //fires events
		}
	    }
	    else if (checkboxStates[checkboxStateCounter].booleanValue() != ((Checkbox) checkbox).getState()) {
		//ditto
		((Checkbox) checkbox).setState(checkboxStates[checkboxStateCounter].booleanValue());
		((Checkbox) checkbox).dispatchEvent(new ItemEvent((ItemSelectable) checkbox,
								  ItemEvent.ITEM_STATE_CHANGED,
								  ((Checkbox) checkbox).getLabel(),
								  //which is normally what goes here for checkbox events
								  ((checkboxStates[checkboxStateCounter].booleanValue())?
								   //specifying new state
								   ItemEvent.SELECTED:
								   ItemEvent.DESELECTED)));
	    }
	}
    }
    private void tryChoosing(Component choice, Class type, int choiceStateCounter) 
	throws IllegalArgumentException {
	//Selects an option from Choice widget, firing appropriate events.
	if (choiceStates == null || choiceStateCounter >= choiceStates.length)
	    throw new IllegalArgumentException("Need to specify more choice states for '"
						     + targetTitle + "' dialog.");
	else if (!choice.isEnabled())
	    return;
	else if (choiceStates[choiceStateCounter] != null) {
	    if (JComboBox.class.isAssignableFrom(type)) {
		int oldIndex=((JComboBox) choice).getSelectedIndex();
		if (oldIndex != choiceStates[choiceStateCounter].intValue()) {
		    ((JComboBox) choice).setSelectedIndex(choiceStates[choiceStateCounter].intValue());
		    fireJComboBoxEvents((JComboBox) choice, oldIndex, choiceStateCounter);
		}
	    }
	    else if (((Choice) choice).getSelectedIndex() != choiceStates[choiceStateCounter].intValue()) {
		((Choice) choice).select(choiceStates[choiceStateCounter].intValue());
		choice.dispatchEvent(new ItemEvent((ItemSelectable) choice, ItemEvent.ITEM_STATE_CHANGED,
						   ((Choice) choice).getSelectedItem(), ItemEvent.SELECTED)); //I hope this is right
	    }
	}
    }
    private void tryMultiChoosing(Component list, Class type, int listStateCounter) 
	throws IllegalArgumentException {
	/*Selects any number of options from a list and makes sure the
	  rest wind up deselected.  In lists that forbid multiple
	  selections, the first of the desired options is the one that
	  gets selected.  ItemEvents and ListSelectionEvents only
	  fired.*/
	if (listStates == null || listStateCounter >= listStates.length)
	    throw new IllegalArgumentException("Need to specify more list states for '"
						     + targetTitle + "' dialog.");
	else if (!list.isEnabled())
	    return;
	else if (listStates[listStateCounter] != null) {
	    Arrays.sort(listStates[listStateCounter]);
	    if (JList.class.isAssignableFrom(type)) {
		int listLength=((JList) list).getModel().getSize();
		for (int listIndex=0, arrayIndex=0; listIndex < listLength; arrayIndex++) {
				//go thru list selecting what's on the list and deselecting what's not
		    int selectThisNext=(arrayIndex < listStates[listStateCounter].length)?
			//arrayIndex overrun avoided here
			listStates[listStateCounter][arrayIndex]:
			listLength; //make sure deselection extends from last wanted item to end of list
		    if (listIndex < selectThisNext)
			//we don't want deselection if the next thing to select IS at listIndex
			((JList) list).removeSelectionInterval(listIndex, selectThisNext-1);
		    /*Also fires event showing selection has changed
                      in interval given.  Does not actually mimic the
                      exact sequence & nature of events a real user
                      selection would fire (through individual presses
                      of the mouse button/arrow keys) because these
                      are arbitrary and no plugin should have reason
                      to base its behavior on them.*/
		    if (selectThisNext < listLength)
			((JList) list).addSelectionInterval(selectThisNext, selectThisNext);
		    listIndex=selectThisNext+1; //just past range already dealt with
		}
	    }
	    else {
		java.awt.List awtList=(java.awt.List) list; //I refuse to type out that cast more than once
		int listLength=awtList.getItemCount();
		if (!awtList.isMultipleMode()) {
		    int oldIndex=awtList.getSelectedIndex();
		    if (oldIndex != listStates[listStateCounter][0]) {
			awtList.deselect(oldIndex);
			awtList.dispatchEvent(new ItemEvent(awtList, ItemEvent.ITEM_STATE_CHANGED,
							    awtList.getItem(oldIndex), ItemEvent.DESELECTED));
			awtList.select(listStates[listStateCounter][0]);
			awtList.dispatchEvent(new ItemEvent(awtList, ItemEvent.ITEM_STATE_CHANGED,
							    awtList.getItem(listStates[listStateCounter][0]),
							    ItemEvent.SELECTED));
		    }
		}
		else for (int listIndex=0, arrayIndex=0; listIndex < listLength; arrayIndex++) {
		    int selectThisNext=(arrayIndex < listStates[listStateCounter].length)?
			listStates[listStateCounter][arrayIndex]:
			listLength;
		    for (; listIndex < selectThisNext; listIndex++)
			//get rid of unwanted selections 1 by 1, List doesn't allow deselecting a range
			if (awtList.isIndexSelected(listIndex)) {
			    awtList.deselect(listIndex);
			    awtList.dispatchEvent(new ItemEvent(awtList, ItemEvent.ITEM_STATE_CHANGED,
								awtList.getItem(listIndex), ItemEvent.DESELECTED));
			}
		    if (selectThisNext < listLength && !awtList.isIndexSelected(selectThisNext)) {
			awtList.select(selectThisNext);
			awtList.dispatchEvent(new ItemEvent(awtList, ItemEvent.ITEM_STATE_CHANGED,
							    awtList.getItem(selectThisNext), ItemEvent.SELECTED));
		    }
		    listIndex=selectThisNext+1;
		}
	    }
	}
    }
    private void tryPushing(Component button, Class type, int buttonStateCounter)
	throws IllegalArgumentException {
	/*If appropriate, fires an ActionEvent to simulate pushing the
	  button without modifier keys.*/
	if (buttonStates == null || buttonStateCounter >= buttonStates.length)
	    throw new IllegalArgumentException("Need to specify more button states for '"
						     + targetTitle + "' dialog.");
	else if (buttonStates[buttonStateCounter] && button.isEnabled()) {
	    if (AbstractButton.class.isAssignableFrom(type))
		((AbstractButton) button).doClick();
	    else
		((Button) button).dispatchEvent(new ActionEvent(button, ActionEvent.ACTION_PERFORMED,
								((Button) button).getActionCommand()));
	}
    }
    private void tryWriting(Component textComponent, Class type, int textComponentStateCounter) 
	throws IllegalArgumentException {
	//Fills a text box with text.  Events sometimes fired.
	if (textComponentStates == null || textComponentStateCounter >= textComponentStates.length)
	    throw new IllegalArgumentException("Need to specify more text-component states for '"
						     + targetTitle + "' dialog.");
	else if (!textComponent.isEnabled())
	    return;
	else if (textComponentStates[textComponentStateCounter] != null) {
	    if (JComboBox.class.isAssignableFrom(type) &&
		!((JComboBox) textComponent).getSelectedItem().equals(textComponentStates[textComponentStateCounter])) {
		int oldIndex=((JComboBox) textComponent).getSelectedIndex();
		((JComboBox) textComponent).setSelectedItem(textComponentStates[textComponentStateCounter]);
		fireJComboBoxEvents((JComboBox) textComponent, oldIndex, textComponentStateCounter);
	    }
	    else if (JTextComponent.class.isAssignableFrom(type) && ((JTextComponent) textComponent).isEditable() &&
		     !((JTextComponent) textComponent).getText().equals(textComponentStates[textComponentStateCounter])) {
		((JTextComponent) textComponent).setText(textComponentStates[textComponentStateCounter]);
	    } /*above line fires DocumentEvents--real typing would
	    also fire CaretEvents but we can't access class they're
	    from in order to fake any*/
	    else if (TextComponent.class.isAssignableFrom(type) && ((TextComponent) textComponent).isEditable() &&
		     !((TextComponent) textComponent).getText().equals(textComponentStates[textComponentStateCounter])) {
		((TextComponent) textComponent).setText(textComponentStates[textComponentStateCounter]);
		//above line fires event
	    }
	}
    }
}
