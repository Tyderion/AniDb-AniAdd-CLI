package kodi;

import lombok.val;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.util.Optional;

public class XmlHelper {

    public static int getIntAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Integer.parseInt(a.getValue())).orElseThrow();
    }

    public static boolean getBooleanAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Boolean.parseBoolean(a.getValue())).orElse(false);
    }

    public static Integer getIntegerAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Integer.parseInt(a.getValue())).orElse(null);
    }

    public static Long getLongAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Long.parseLong(a.getValue())).orElse(null);
    }

    public static String getStringAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(Attribute::getValue).orElse(null);
    }

    public static Optional<Attribute> getAttribute(StartElement startElement, String name) {
        return Optional.ofNullable(startElement.getAttributeByName(new QName(name)));
    }
}
