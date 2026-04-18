package live;

import exchange.CrowdQExchange;
import exchange.CrowdQExchangeTag;

public class LiveCoordinator {
    public interface CommandSink {
        void executeCommand(String command, int argument);

        void loadNewShow(String showName);
    }

    private final CommandSink commandSink;

    public LiveCoordinator(CommandSink commandSink) {
        this.commandSink = commandSink;
    }

    public void onExchangePacket(CrowdQExchange exchange) {
        if (exchange == null) {
            return;
        }

        if (exchange.tag == CrowdQExchangeTag.LOAD) {
            commandSink.loadNewShow(exchange.payload);
        } else if (exchange.tag == CrowdQExchangeTag.COMMAND) {
            commandSink.executeCommand(exchange.payload, exchange.argument);
        }
    }
}