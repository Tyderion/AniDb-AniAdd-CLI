package aniAdd.kodi.jsonrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

public abstract class KodiJsonRpc {
	public final static String JSONRPC_VERSION = "2.0";
	public final static String PROPERTY_METHOD = "method";
	public final static String PROPERTY_JSONRPC = "jsonrpc";
	public final static String PROPERTY_PARAMS = "params";
	public final static String PROPERTY_ID = "id";

	@Getter private final String methodName;
	private JsonObject jsonObject;
	private JsonObject params;

	public KodiJsonRpc(String methodName) {
		this.methodName = methodName;
		jsonObject = new JsonObject();
		params = new JsonObject();
		jsonObject.add(PROPERTY_PARAMS, params);
		jsonObject.addProperty(PROPERTY_JSONRPC, JSONRPC_VERSION);
		jsonObject.addProperty(PROPERTY_METHOD, methodName);
		jsonObject.addProperty(PROPERTY_ID, 1);
	}

	protected void addProperty(String property, String value) {
		jsonObject.addProperty(property, value);
	}

	protected void addProperty(String property, Number value) {
		jsonObject.addProperty(property, value);
	}

	protected void addProperty(String property, Boolean value) {
		jsonObject.addProperty(property, value);
	}

	protected void addProperty(String property, Character value) {
		jsonObject.addProperty(property, value);
	}

	protected void addProperty(String property, JsonElement value) {
		jsonObject.add(property, value);
	}

	protected void addParam(String property, String value) {
		params.addProperty(property, value);
	}

	protected void addParam(String property, Boolean value) {
		params.addProperty(property, value);
	}

	protected void addParam(String property, Character value) {
		params.addProperty(property, value);
	}

	protected void addParam(String property, Number value) {
		params.addProperty(property, value);
	}

	protected void addParam(String property, JsonElement value) {
		params.add(property, value);
	}

	public String getAsJsonString() {
		return jsonObject.toString();
	}

	public JsonObject getAsJsonObject() {
		return jsonObject;
	}

}