package me.botsko.prism.actiontypes;

public class BlockFormType extends GenericActionType {
	
	
	/**
	 * 
	 * @return
	 */
	public String getActionType(){
		return "block-form";
	}
	
	
	/**
	 * 
	 * @return
	 */
	public boolean doesCreateBlock(){
		return true;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public String getNiceWordOfAction(){
		return "formed";
	}
}