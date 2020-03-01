package edu.cmu.hcii.sugilite.sovite.dialog.intent_handler;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.pumice.communication.PumiceInstructionPacket;
import edu.cmu.hcii.sugilite.pumice.communication.PumiceSemanticParsingResultPacket;
import edu.cmu.hcii.sugilite.pumice.communication.SkipPumiceJSONSerialization;
import edu.cmu.hcii.sugilite.pumice.dialog.PumiceDialogManager;
import edu.cmu.hcii.sugilite.pumice.dialog.intent_handler.PumiceUtteranceIntentHandler;
import edu.cmu.hcii.sugilite.pumice.kb.PumiceKnowledgeManager;
import edu.cmu.hcii.sugilite.pumice.kb.PumiceProceduralKnowledge;
import edu.cmu.hcii.sugilite.sovite.SoviteAppNameAppInfoManager;
import edu.cmu.hcii.sugilite.sovite.communication.SoviteAppResolutionQueryPacket;
import edu.cmu.hcii.sugilite.sovite.communication.SoviteAppResolutionResultPacket;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.SugiliteVerbalInstructionHTTPQueryInterface;

/**
 * @author toby
 * @date 2/24/20
 * @time 3:11 PM
 */
public class SoviteIntentClassificationErrorIntentHandler implements PumiceUtteranceIntentHandler, SugiliteVerbalInstructionHTTPQueryInterface {

    private PumiceDialogManager pumiceDialogManager;
    private Activity context;
    private String originalUtterance;
    private PumiceSemanticParsingResultPacket originalSemanticParsingResult;
    private SoviteAppNameAppInfoManager soviteAppNameAppInfoManager;
    private Calendar calendar;

    private final static String RELEVANT_APPS_FOR_UTTERANCES = "RELEVANT_APPS_FOR_UTTERANCES";
    private final static String RELEVANT_UTTERANCES_FOR_APPS = "RELEVANT_UTTERANCES_FOR_APPS";

    public SoviteIntentClassificationErrorIntentHandler(PumiceDialogManager pumiceDialogManager, Activity context, String originalUtterance, PumiceSemanticParsingResultPacket originalSemanticParsingResult) {
        this.pumiceDialogManager = pumiceDialogManager;
        this.context = context;
        this.calendar = Calendar.getInstance();
        this.originalUtterance = originalUtterance;
        this.originalSemanticParsingResult = originalSemanticParsingResult;
        this.soviteAppNameAppInfoManager = SoviteAppNameAppInfoManager.getInstance(SugiliteData.getAppContext());
    }

    @Override
    public void sendPromptForTheIntentHandler() {
        pumiceDialogManager.sendAgentMessage(String.format("What app should I use to %s?", originalUtterance.toLowerCase()), true, true);
    }

    @Override
    public void handleIntentWithUtterance(PumiceDialogManager dialogManager, PumiceIntent pumiceIntent, PumiceDialogManager.PumiceUtterance utterance) {
        //TODO: add speech recognition bias
        if (pumiceIntent.equals(PumiceIntent.APP_REFERENCE)) {
            // the user has provided an app name
            List<String> availableAppNames = new ArrayList<>(soviteAppNameAppInfoManager.getAllAvailableAppPackageNameReadableNameMap(true).values());
            PumiceInstructionPacket pumiceInstructionPacket = new PumiceInstructionPacket(dialogManager.getPumiceKnowledgeManager(), PumiceIntent.APP_REFERENCE, calendar.getTimeInMillis(), utterance.getContent(), availableAppNames);
            try {
                dialogManager.getHttpQueryManager().sendPumiceInstructionPacketOnASeparateThread(pumiceInstructionPacket, this);
            } catch (Exception e) {
                //TODO: error handling
                e.printStackTrace();
                pumiceDialogManager.sendAgentMessage("Failed to send the query", true, false);
            }


        } else if (pumiceIntent.equals(PumiceIntent.UNRECOGNIZED)) {
            pumiceDialogManager.sendAgentMessage("I can't recognize your response. Please respond with \"Yes\" or \"No\".", true, false);
            sendPromptForTheIntentHandler();
        }
    }

    @Override
    public void setContext(Activity context) {
        this.context = context;
    }

    @Override
    public PumiceIntent detectIntentFromUtterance(PumiceDialogManager.PumiceUtterance utterance) {
        //TODO: handle situations where the user can't provide an app name
        return PumiceIntent.APP_REFERENCE;
    }

    @Override
    public void resultReceived(int responseCode, String result, String originalQuery) {
        //handle the parser's result
        if (result.contains("APP_REFERENCE")) {
            Gson gson = new GsonBuilder()
                    .addSerializationExclusionStrategy(new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            return f.getAnnotation(SkipPumiceJSONSerialization.class) != null;
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                            return false;
                        }
                    })
                    .create();
            try {
                PumiceSemanticParsingResultPacket resultPacket = gson.fromJson(result, PumiceSemanticParsingResultPacket.class);
                resultPacket.cleanFormula();
                if (resultPacket.utteranceType != null) {
                    switch (resultPacket.utteranceType) {
                        case "APP_REFERENCE":
                            if (resultPacket.queries != null && resultPacket.queries.size() > 0) {
                                //parse out app names from the top query formula
                                String appName = soviteAppNameAppInfoManager.extractStringFromStringValueFormula(resultPacket.queries.get(0).formula);
                                if (resultPacket.userUtterance.toLowerCase().contains(appName.toLowerCase())) {
                                    // pumiceDialogManager.sendAgentMessage(String.format("OK, I will %s in %s", originalUtterance, appName), true, false);

                                    //try to match with the app name
                                    String packageName = soviteAppNameAppInfoManager.getAppReadableNameAppPackageNameMap(false).get(appName);
                                    if (packageName != null) {
                                        Drawable icon = soviteAppNameAppInfoManager.getApplicationIconFromPackageName(packageName);
                                        if (icon != null) {
                                            ImageView imageView = new ImageView(context);
                                            imageView.setImageDrawable(icon);
                                            //send the icon of the selected app
                                            pumiceDialogManager.sendAgentViewMessage(imageView, String.format("ICON: %s", appName), false, false);
                                        }

                                        //1. check if we have other scripts that use this app
                                        PumiceKnowledgeManager knowledgeManager = pumiceDialogManager.getPumiceKnowledgeManager();
                                        List<PumiceProceduralKnowledge> pumiceProceduralKnowledges = knowledgeManager.getPumiceProceduralKnowledges();
                                        List<PumiceProceduralKnowledge> proceduralKnowledgesWithMatchedApps = new ArrayList<>();
                                        List<String> allAvailableScriptUtterances = new ArrayList<>();

                                        for (PumiceProceduralKnowledge pumiceProceduralKnowledge : pumiceProceduralKnowledges) {
                                            allAvailableScriptUtterances.add(pumiceProceduralKnowledge.getProcedureDescription(knowledgeManager));
                                            List<String> involvedAppNames = pumiceProceduralKnowledge.getInvolvedAppNames(knowledgeManager);
                                            if (involvedAppNames != null && involvedAppNames.contains(appName)) {
                                                proceduralKnowledgesWithMatchedApps.add(pumiceProceduralKnowledge);
                                            }
                                        }
                                        if (proceduralKnowledgesWithMatchedApps.size() > 0) {
                                            // able to find other procedures that use this app
                                            //use a new handler to handle
                                            SoviteScriptsWithTheSameAppDisambiguationIntentHandler soviteScriptsWithTheSameAppDisambiguationIntentHandler = new SoviteScriptsWithTheSameAppDisambiguationIntentHandler(pumiceDialogManager, context, packageName, appName, originalUtterance, proceduralKnowledgesWithMatchedApps);
                                            pumiceDialogManager.updateUtteranceIntentHandlerInANewState(soviteScriptsWithTheSameAppDisambiguationIntentHandler);
                                            pumiceDialogManager.callSendPromptForTheIntentHandlerForCurrentIntentHandler();

                                        } else {
                                            // unable to find other procedures that use this app
                                            pumiceDialogManager.sendAgentMessage(String.format("I can't find other scripts that use %s.", appName), true, false);
                                            //2. check if we have other scripts that are similar to the embeddings of this app
                                            List<String> appPackageNames = new ArrayList<>();
                                            appPackageNames.add(packageName);

                                            SoviteAppResolutionQueryPacket soviteAppResolutionQueryPacket = new SoviteAppResolutionQueryPacket("playstore", RELEVANT_UTTERANCES_FOR_APPS, allAvailableScriptUtterances, appPackageNames);
                                            pumiceDialogManager.sendAgentMessage(String.format("I will search for scripts that are relevant to %s.", appName), true, false);
                                            pumiceDialogManager.getHttpQueryManager().sendSoviteAppResolutionPacketOnASeparateThread(soviteAppResolutionQueryPacket, this);
                                            // pumiceDialogManager.sendAgentMessage(String.format("Here are scripts are are relevant to %s", appName), true, false);
                                        }

                                        // retrieve the play store description and snapshots for packageName, and calculate the BERT vector
                                        /*
                                        List<String> allPackageNames = new ArrayList<>(soviteAppNameAppInfoManager.getAllAvailableAppPackageNameReadableNameMap(true).keySet());
                                        List<String> utteranceList = new ArrayList<>();
                                        utteranceList.add(originalUtterance);
                                        SoviteAppResolutionQueryPacket soviteAppResolutionQueryPacket = new SoviteAppResolutionQueryPacket("playstore", RELEVANT_APPS_FOR_UTTERANCES, utteranceList, allPackageNames);
                                        pumiceDialogManager.getHttpQueryManager().sendSoviteAppResolutionPacketOnASeparateThread(soviteAppResolutionQueryPacket, this);
                                        */
                                    }

                                } else {
                                    throw new RuntimeException("the utterance does not contain the app name");
                                }
                            } else {
                                throw new RuntimeException("empty server result");
                            }
                            break;
                        default:
                            throw new RuntimeException("wrong type of result");
                    }
                }
            } catch (Exception e) {
                //TODO: error handling
                if (e.getMessage().contains("empty server result")) {
                    pumiceDialogManager.sendAgentMessage("Empty server response", true, false);
                } else {
                    pumiceDialogManager.sendAgentMessage("Can't read from the server response", true, false);
                }
                pumiceDialogManager.sendAgentMessage("OK. Let's try again.", true, false);
                pumiceDialogManager.updateUtteranceIntentHandlerInANewState(this);
                sendPromptForTheIntentHandler();
                e.printStackTrace();
            }
        } else if (result.contains(RELEVANT_APPS_FOR_UTTERANCES)) {
            //handle queries of getting relevant apps for utterances
            Gson gson = new GsonBuilder()
                    .addSerializationExclusionStrategy(new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            return f.getAnnotation(SkipPumiceJSONSerialization.class) != null;
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                            return false;
                        }
                    })
                    .create();
            try {
                SoviteAppResolutionResultPacket resultPacket = gson.fromJson(result, SoviteAppResolutionResultPacket.class);
                pumiceDialogManager.sendAgentMessage("Here are the relevant apps for your utterance", false, false);
                pumiceDialogManager.sendAgentMessage(resultPacket.getResult_map().toString(), false, false);

            } catch (Exception e) {
                pumiceDialogManager.sendAgentMessage("Can't read from the server response", true, false);
                pumiceDialogManager.sendAgentMessage("OK. Let's try again.", true, false);
                pumiceDialogManager.updateUtteranceIntentHandlerInANewState(this);
                sendPromptForTheIntentHandler();
                e.printStackTrace();
            }
        } else if (result.contains(RELEVANT_UTTERANCES_FOR_APPS)) {
            //handle queries of getting relevant utterances for apps
            Gson gson = new GsonBuilder()
                    .addSerializationExclusionStrategy(new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            return f.getAnnotation(SkipPumiceJSONSerialization.class) != null;
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                            return false;
                        }
                    })
                    .create();
            try {
                SoviteAppResolutionResultPacket resultPacket = gson.fromJson(result, SoviteAppResolutionResultPacket.class);
                Map<String, List<String>> appRelevantUtteranceMap = resultPacket.getResult_map();
                SoviteAppNameAppInfoManager soviteAppNameAppInfoManager = SoviteAppNameAppInfoManager.getInstance(context);

                for (String appPackageName : appRelevantUtteranceMap.keySet()) {
                    String appReadableName = soviteAppNameAppInfoManager.getReadableAppNameForPackageName(appPackageName);
                    pumiceDialogManager.sendAgentMessage(String.format("Here are the relevant scripts for the app %s:", appReadableName), true, false);
                    pumiceDialogManager.sendAgentMessage(appRelevantUtteranceMap.get(appPackageName).toString(), false, false);
                    //TODO: need a new intent handler here
                }
            } catch (Exception e) {
                pumiceDialogManager.sendAgentMessage("Can't read from the server response", true, false);
                pumiceDialogManager.sendAgentMessage("OK. Let's try again.", true, false);
                pumiceDialogManager.updateUtteranceIntentHandlerInANewState(this);
                sendPromptForTheIntentHandler();
                e.printStackTrace();
            }
        }

    }
}
