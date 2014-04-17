package gloworm;

import java.lang.reflect.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.gui.*;

public class EventDrivenDialog extends GenericDialog {
    /*GenericDialog that can add pre-existing components to itself,
      integrating them into its layout and the set of its components
      that can be accessed by the usual selector methods.  Can also
      take methods defined elsewhere for use in handling events.  This
      allows the creation of a GenericDialog that responds to the user
      in real time with behavior that can be specified without
      subclassing.*/

    /*FIXME: streamline (too much redundant code) and break reliance
      on reflection--it's too limited, and if client can provide the
      widgets it can listen to them */

    protected Method
	actionResponse,
	focusResponse,
	itemResponse,
	keyResponse,
	textResponse;
    //event handling methods
    protected Object
	instantiator;
    /*Object containing the method that instantiated this
      EventDrivenDialog.  Presumably it also contains the methods
      being passed as actionResponse, focusResponse etc. and can be
      asked to run them...*/
    protected GridBagLayout
	grid;   //can't get at this from superclass.  FIXME: see if can eliminate
    protected GridBagConstraints
  	c; //ditto
    private int
	y;
    private boolean
	wasReallyCanceled;
    //super.wasCanceled is wrong (in some versions) & can't be reached from here to correct it
	
	
    EventDrivenDialog(String title) {
	super(title);
	c=new GridBagConstraints();
	grid=(GridBagLayout) super.getLayout();
    }
    EventDrivenDialog(String title, Frame parent) {
	super(title, parent);
	c=new GridBagConstraints();
	grid=(GridBagLayout) super.getLayout();
    }
    EventDrivenDialog(String title, Frame parent, Method actionResponse,
		      Method focusResponse, Method itemResponse, Method keyResponse,
		      Method textResponse, Object instantiator) {
	/*see above*/
	super(title, parent);
	c=new GridBagConstraints();
	grid=(GridBagLayout) super.getLayout();
	this.actionResponse=actionResponse;
	this.focusResponse=focusResponse;
	this.itemResponse=itemResponse;
	this.keyResponse=keyResponse;
	this.textResponse=textResponse;
	this.instantiator=instantiator;
    }
    EventDrivenDialog(String title, Method actionResponse, Method focusResponse,
		      Method itemResponse, Method keyResponse, Method textResponse,
		      Object instantiator) {
	/*Specifies responses to the 5 events a GenericDialog can listen for.  Note that one
	response is used for every type of FocusEvent, KeyEvent, etc.--so the response
	methods passed should do any needed dispatching on the event's type.*/
	super(title);
	c=new GridBagConstraints();
	grid=(GridBagLayout) super.getLayout();
	this.actionResponse=actionResponse;
	this.focusResponse=focusResponse;
	this.itemResponse=itemResponse;
	this.keyResponse=keyResponse;
	this.textResponse=textResponse;
	this.instantiator=instantiator;
    }
    public final void actionPerformed(ActionEvent e) {
	respondToAction(e);
    }
    public void addButton(Button button) {
	Component dummy=null;
	addMessage(button.getLabel()); //create dummy
	Component[] innards=getComponents();
	for (int index=0; index < innards.length; index++)
	    if (innards[index] instanceof Label &&
		((Label) innards[index]).getText()==button.getLabel())
		dummy=innards[index];
	//we found the dummy, now replace it with button:
        grid.setConstraints(button, grid.getConstraints(dummy));
	remove(dummy);
	add(button);
    }
    public void addCheckbox(Checkbox cb) {
	super.addCheckbox("", false);
	replaceDummyComponent(checkbox, cb);
    }
    public void addChoice (String label, Choice ch) {
	String longestItem=ch.getItem(0);
	for (int index=0; index < ch.getItemCount(); index++)
	    if (longestItem.length() < ch.getItem(index).length())
		longestItem = ch.getItem(index);
	super.addChoice(label, new String[] {longestItem}, "1");
	//make sure layout gets done as ch will need it
	replaceDummyComponent(choice, ch);
    }
    public void addDivider(int length) {
	/*Makes a horizontal line of underscores length characters
          long to serve as a divider between sections.*/
	String dividerString="";
	Component[] innards;
	for (int index=0; index < length; index++, dividerString += '_');
	addMessage(dividerString);
	innards=getComponents();
	for (int index=0; index < innards.length; index++)
	    if (innards[index] instanceof Label &&
		((Label) innards[index]).getText()==dividerString)
		((Label) innards[index]).setEnabled(false);
	//provide grayed-out effect, at least in Windows
    }
    public void addMessage(Label message) {
	Component dummy=null;
	addMessage(message.getText()==null?  "": message.getText());
	Component[] innards=getComponents();
	for (int index=0; index < innards.length; index++)
	    if (innards[index] instanceof Label &&
		((Label) innards[index]).getText()==message.getText())
		dummy=innards[index];
	//take its layout and use for message:
	grid.setConstraints(message, grid.getConstraints(dummy));
	remove(dummy);
	add(message);
    }
    public void addNumericField(String label, TextField tf) {
	super.addNumericField(label, 0, 0);
	replaceDummyComponent(numberField, tf);
    }
    public void addNumericField(String label, TextField tf, String units) {
	/*This method imitates the functionality of the
          GenericDialog.addNumericField() method that takes a units
          argument.  In versions of ImageJ (pre-1.3) that do not have
          the corresponding GenericDialog method, this method still
          works, but the units are shown differently. */
	try {
	    //use reflection to access super.addNumericField so can still compile & run w/o it:
	    GenericDialog.class.getMethod("addNumericField",
					  new Class[] {String.class,
						       double.class,
						       int.class, int.class,
						       String.class}).invoke(this,
									     new Object[] {label,
											   new Double(0),
											   new Integer(0),
											   new Integer(tf.getColumns()),
											   units});
	    replaceDummyComponent(numberField, tf);
	} catch (IllegalAccessException e) { //shouldn't happen, invoked method is public:
	    throw new InternalError("Public method (GenericDialog.addNumericField) isn't");
	} catch (InvocationTargetException e) { //shouldn't happen, invoked method throws nothing:
	    throw new InternalError("Unexpected exception in GenericDialog.addNumericField: " + e.getTargetException().getMessage());
	} catch (NoSuchMethodException e) {
	    //only way to show units is to integrate into label:
	    if (units.startsWith("(") && units.endsWith(")"))
		units=new String(new StringBuffer("(").append(label).append(')'));
	    addNumericField(new String(new StringBuffer(label).append(' ').append(units)), tf);
	}
    }
    public void addStringField(String label, TextField tf) {                	
	super.addStringField(label, "");
	replaceDummyComponent(stringField, tf);
    }
    private void assertParameterListMatches(Method m, Class[] args)
	throws IllegalArgumentException{
	/*Throws exception if the types in args are not the same as
          the parameter types of m.*/
	Class[] parameterTypes=m.getParameterTypes();
	boolean typesMatch=(parameterTypes.length==args.length);
	for (int index=1; typesMatch && index < parameterTypes.length; index++)
	    typesMatch=typesMatch && (parameterTypes[index].equals(args[index]));
	if (!typesMatch)
	    throw new IllegalArgumentException(m + " has wrong signature");
    }
    protected final boolean buttonIsLabeled(Button theButton, String theLabel) {
	/*Returns true iff theButton's label is the same as theLabel,
          ignoring case and leading/trailing whitespace.*/
	return theButton.getLabel().trim().equalsIgnoreCase(theLabel.trim());
    }
    private boolean defaultActionResponse(ActionEvent e) {
	/*Handles OK/cancel button events.  Returns true iff e's source is OK or Cancel button.*/
	Object source=e.getSource();
	if (source instanceof Button) {
	    if (buttonIsLabeled((Button) source, "ok"))
		wasReallyCanceled=false;
	    else if (buttonIsLabeled((Button) source, "cancel"))
		wasReallyCanceled=true;
	    /*super's bad habit of instantiating new buttons w/every
              call to showDialog() leaves no other way to tell
              accurately which button was pushed*/
	    else return false;
	    //stolen from super's code:
	    setVisible(false);
	    dispose();
	    return true;
	}
	else return false;
    }
    public final void focusGained(FocusEvent e) {
	respondToFocus(e);
    }
    public final void focusLost(FocusEvent e) {
	respondToFocus(e);
    }
    public boolean hasAnyInvalidNumbers() {
	/*Returns true iff any of the numeric fields contains a string
          that can't be read as a decimal number.  Unlike
          invalidNumber(), detects bad input to fields that have not
          yet been read with getNextNumber(), and continues to return
          true on future calls until the string is corrected.  Less
          efficient though.*/
	for (int index=0; index < numberField.size(); index++) {
	    try  {
		new Double(((TextField) numberField.elementAt(index)).getText());
	    }
	    catch (NumberFormatException e) {
		return true;
	    }
	    //based on GenericDialog.invalidNumber() source
	}
	return false;
    }
    public final void itemStateChanged(ItemEvent e) {
	respondToItem(e);
    }
    public final void keyPressed(KeyEvent e) {
	respondToKey(e);
    }
    public final void keyReleased(KeyEvent e) {
	respondToKey(e);
    }
    public final void keyTyped(KeyEvent e) {
	respondToKey(e);
    }
    private void replaceDummyComponent(Vector componentStorage, Component replacement) {
	/*Does the part of various add methods where a GenericDialog-generated
	  component is replaced with one passed as an argument.*/
	Component dummy=(Component) componentStorage.elementAt(componentStorage.size()-1);
	Container parent=dummy.getParent(); //not always this
	int targetIndex=Arrays.asList(parent.getComponents()).indexOf(dummy);
	if (parent==this) grid.setConstraints(replacement, grid.getConstraints(dummy));
	parent.remove(dummy);
	parent.add(replacement, targetIndex);
	componentStorage.setElementAt(replacement, componentStorage.size()-1);
    }
    private void respondToAction(ActionEvent e) {
	if (!defaultActionResponse(e) && !(actionResponse==null))
	    try {
		actionResponse.invoke(instantiator, 
				      new ActionEvent[] {e});
	    } catch (Exception x) {
		IJ.showMessage(this + ":\n" + x);
	    }
    }
    private void respondToFocus(FocusEvent e) {
	if (!(focusResponse==null))
	    try {
		Object discardThisObject=focusResponse.invoke(instantiator,
							      new FocusEvent[] {e});
	    } catch (Exception x) {
		IJ.showMessage(this + ":\n" + x);
	    }
    }
    private void respondToItem(ItemEvent e) {
	if (!(itemResponse==null))
	    try {
		Object discardThisObject=itemResponse.invoke(instantiator,
							     new ItemEvent[] {e});
	    } catch (Exception x) {
		IJ.showMessage(this + ":\n" + x);
		if (x instanceof InvocationTargetException) {
		    Throwable oops=((InvocationTargetException) x).getTargetException();
		    java.io.CharArrayWriter caw = new java.io.CharArrayWriter();
		    java.io.PrintWriter pw = new java.io.PrintWriter(caw);
		    oops.printStackTrace(pw);
		    String s = caw.toString();
		    new ij.text.TextWindow("Exception", s, 350, 250);
		}
	    }
    }
    private void respondToKey(KeyEvent e) {
	if (!(keyResponse==null))
	    try {
		Object discardThisObject=keyResponse.invoke(instantiator,
							    new KeyEvent[] {e});
	    } catch (Exception x) {
		IJ.showMessage(this + ":\n" + x);
	    }
    }
    private void respondToText(TextEvent e) {
	if (!(textResponse==null))
	    try {
		Object discardThisObject=textResponse.invoke(instantiator,
							     new TextEvent[] {e});
	    } catch (Exception x) {
		IJ.showMessage(this + ":\n" + x);
	    }
    }
    public final void setActionResponse(Method actionResponse) {
	assertParameterListMatches(actionResponse, new Class[] {ActionEvent.class});
	this.actionResponse=actionResponse;
    }
    public final void setFocusResponse(Method focusResponse) {
	assertParameterListMatches(focusResponse, new Class[] {FocusEvent.class});
	this.focusResponse=focusResponse;
    }
    public final void setItemResponse(Method itemResponse) {
	assertParameterListMatches(itemResponse, new Class[] {ItemEvent.class});
	this.itemResponse=itemResponse;
    }
    public final void setKeyResponse(Method keyResponse) {
	assertParameterListMatches(keyResponse, new Class[] {KeyEvent.class});
	this.keyResponse=keyResponse;
    }
    public final void setTextResponse(Method textResponse) {
	assertParameterListMatches(keyResponse, new Class[] {TextEvent.class});
	this.textResponse=textResponse;
    }
//      protected final Label makeLabel(String label) {
//  	/*Stolen directly from ImageJ source--couldn't
//  	  be accessed otherwise.*/
//  	if (IJ.isMacintosh())
//  	    label += " ";
//  	return new Label(label);
//      }
	
    public void showDialog() {
	validate();
	super.showDialog();
    }
    public final void textValueChanged(TextEvent e) {
	respondToText(e);
    }
    public boolean wasCanceled() {
	return wasReallyCanceled;
    }
}
