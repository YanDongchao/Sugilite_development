package edu.cmu.hcii.sugilite.model.variable;

import java.io.Serializable;

/**
 * @author toby
 * @date 7/11/16
 * @time 4:53 PM
 */
public class Variable implements Serializable {
    private String name;
    final static public int USER_INPUT = 1, LOAD_RUNTIME = 2;
    public int type;
    public Variable(String name){
        super();
        type = USER_INPUT;
        this.name = name;
    }
    public Variable(int type){
        super();
        this.type = type;
    }
    public Variable(int type, String name){
        super();
        this.type = type;
        this.name = name;
    }
    public Variable(){
        type = USER_INPUT;
    }
    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name = name;
    }
}
