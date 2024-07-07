package udpapi2.command;

import lombok.Data;

@Data
public class CommandWrapper {
    private final Command command;

    public String getTag(){
        return command.getFullTag();
    }
}
