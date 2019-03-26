package edu.cmu.hcii.sugilite.pumice.dialog.intent_handler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import edu.cmu.hcii.sugilite.pumice.dialog.PumiceDialogManager;
import edu.cmu.hcii.sugilite.pumice.dialog.demonstration.PumiceProcedureDemonstrationDialog;
import edu.cmu.hcii.sugilite.pumice.kb.PumiceBooleanExpKnowledge;
import edu.cmu.hcii.sugilite.pumice.kb.PumiceProceduralKnowledge;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.SugiliteVerbalInstructionHTTPQueryInterface;

/**
 * @author toby
 * @date 12/4/18
 * @time 4:15 PM
 */

//class used for handle utterances when the user explain a PumiceProceduralKnowledge
public class PumiceUserExplainProcedureIntentHandler implements PumiceUtteranceIntentHandler, SugiliteVerbalInstructionHTTPQueryInterface {
    private transient Activity context;
    private transient PumiceDialogManager pumiceDialogManager;
    private String parentKnowledgeName;

    //need to notify this lock when the procedure is resolved, and return the value through this object
    private PumiceProceduralKnowledge resolveProcedureLock;
    Calendar calendar;


    public PumiceUserExplainProcedureIntentHandler(PumiceDialogManager pumiceDialogManager, Activity context, PumiceProceduralKnowledge resolveProcedureLock, String parentKnowledgeName){
        this.pumiceDialogManager = pumiceDialogManager;
        this.context = context;
        this.calendar = Calendar.getInstance();
        this.resolveProcedureLock = resolveProcedureLock;
        this.parentKnowledgeName = parentKnowledgeName;
    }

    @Override
    public void setContext(Activity context) {
        this.context = context;
    }

    @Override
    public void handleIntentWithUtterance(PumiceDialogManager dialogManager, PumiceIntent pumiceIntent, PumiceDialogManager.PumiceUtterance utterance) {

        if (pumiceIntent.equals(PumiceIntent.DEFINE_PROCEDURE_EXP)){
            //for situations e.g., redirection
            dialogManager.sendAgentMessage("I have received your explanation: " + utterance.getContent(), true, false);
            //TODO: send out the server query
            //test
            List<String> appList = new ArrayList<>();
            appList.add("Test App 2");
            returnUserExplainProcedureResult(new PumiceProceduralKnowledge(parentKnowledgeName, utterance.getContent(), appList));
        }

        else if (pumiceIntent.equals(PumiceIntent.DEFINE_PROCEDURE_DEMONSTATION)){
            PumiceProcedureDemonstrationDialog procedureDemonstrationDialog = new PumiceProcedureDemonstrationDialog(context, parentKnowledgeName, utterance.getContent(), dialogManager.getSharedPreferences(), dialogManager.getSugiliteData(), dialogManager.getServiceStatusManager(), this);
            dialogManager.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    //the show() method for the dialog needs to be called at the main thread
                    procedureDemonstrationDialog.show();
                }
            });
            //send out the prompt
            dialogManager.sendAgentMessage("Please start demonstrating how to " + parentKnowledgeName +  ". " + "Click OK to continue.",true, false);
        }
        //set the intent handler back to the default one
        dialogManager.updateUtteranceIntentHandlerInANewState(new PumiceDefaultUtteranceIntentHandler(pumiceDialogManager, context));
    }

    @Override
    public PumiceIntent detectIntentFromUtterance(PumiceDialogManager.PumiceUtterance utterance) {
        if (utterance.getContent().contains("demonstrate")){
            return PumiceIntent.DEFINE_PROCEDURE_DEMONSTATION;
        } else {
            return PumiceIntent.DEFINE_PROCEDURE_EXP;
        }
    }

    public PumiceDialogManager getPumiceDialogManager() {
        return pumiceDialogManager;
    }

    @Override
    public void resultReceived(int responseCode, String result) {
        //TODO: handle server response
        //notify the thread for resolving unknown bool exp that the intent has been fulfilled
    }

    @Override
    public void runOnMainThread(Runnable r) {
        pumiceDialogManager.runOnMainThread(r);
    }

    /**
     * return the result PumiceProceduralKnowledge, and release the lock in the original PumiceInitInstructionParsingHandler
     * @param proceduralKnowledge
     */
    public void returnUserExplainProcedureResult(PumiceProceduralKnowledge proceduralKnowledge){
        synchronized (resolveProcedureLock) {
            resolveProcedureLock.copyFrom(proceduralKnowledge);
            resolveProcedureLock.notify();
        }
    }



}