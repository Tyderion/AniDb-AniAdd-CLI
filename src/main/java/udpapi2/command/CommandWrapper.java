package udpapi2.command;

import lombok.Data;

@Data
public class CommandWrapper {
    private final Command command;

    public String getFullTag(){
        return command.getFullTag();
    }

    public String getTag(){
        return command.getTag();
    }
}
