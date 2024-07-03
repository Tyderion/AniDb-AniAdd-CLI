package aniAdd;

import aniAdd.Modules.IModule;
import aniAdd.misc.Misc;

import java.util.*;

/**
 *
 * @author Arokh
 */
public interface Communication {
	void addComListener(ComListener comListener);

	interface ComListener extends EventListener {
		void handleEvent(CommunicationEvent communicationEvent);
	}

	class CommunicationEvent extends EventObject {
		Date createdOn;
		EventType eventType;
		ArrayList<Object> params;

		public CommunicationEvent(Object source, EventType eventType, Object... params) {
			this(source, eventType);

			this.params = new ArrayList<>();
            Collections.addAll(this.params, params);
		}

		public CommunicationEvent(Object source, EventType eventType) {
			this(source);
			this.eventType = eventType;
		}

		private CommunicationEvent(Object source) {
			super(source);
			createdOn = new Date();
		}

		public EventType EventType() {
			return eventType;
		}

		public Object Params(int i) {
			return params.get(i);
		}

		public int ParamCount() {
			return params.size();
		}

		@Override
		public String toString() {
			StringBuilder str;
			str = new StringBuilder(Misc.DateToString(createdOn, "HH:mm:ss") + " " + eventType + ": " + (getSource() instanceof IModule ? (((IModule) getSource()).ModuleName()) : ""));
			for(int i = 0; i < ParamCount(); i++) str.append(" ").append(Params(i));

			return str.toString();
		}

		public enum EventType {
			Debug, Information, Manipulation, Warning, Error, Fatal
		}
	}
}
