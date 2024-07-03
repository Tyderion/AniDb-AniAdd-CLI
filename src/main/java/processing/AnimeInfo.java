package processing;

import java.io.File;
import java.util.EnumSet;
import java.util.TreeMap;

public class AnimeInfo {
    public AnimeInfo(File folderObj, int id) {
        this.folderObj = folderObj;
        this.id = id;
        actionsTodo = EnumSet.of(eAction.Process);
        actionsDone = EnumSet.noneOf(eAction.class);
        actionsError =EnumSet.noneOf(eAction.class);
        data = new TreeMap<String, String>();
    }

    private File folderObj;
    private int id;
    private EnumSet<AnimeInfo.eAction> actionsTodo;
    private EnumSet<AnimeInfo.eAction> actionsDone;
    private EnumSet<AnimeInfo.eAction> actionsError;
    private TreeMap<String, String> data;
    private boolean isFinal;

    public enum eAction { Process, AnimeCmd, GenerateNfo, }

    public Integer Id() { return id; }
    public File FileObj() { return folderObj; }
    public EnumSet<eAction> ActionsTodo() { return actionsTodo; }
    public EnumSet<eAction> ActionsDone() { return actionsDone; }
    public EnumSet<eAction> ActionsError() { return actionsError; }
    public TreeMap<String, String> Data() { return data; }
    public boolean IsFinal() { return isFinal; }

    public void FileObj(File fileObj) { this.folderObj = fileObj; }
    public void ActionsTodo(EnumSet<eAction> actionsTodo) { this.actionsTodo = actionsTodo; }
    public void ActionsDone(EnumSet<eAction> actionsDone) { this.actionsDone = actionsDone; }
    public void ActionsError(EnumSet<eAction> actionsError) { this.actionsError = actionsError; }
    public void Data(TreeMap<String, String> data) { this.data = data; }
    public void IsFinal(boolean isFinal) { this.isFinal = isFinal; }
}
