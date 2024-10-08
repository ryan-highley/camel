/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;

/**
 * Converts from some DOM types to Java types
 */
@Converter(generateBulkLoader = true)
public final class DomConverter {
    private final XmlConverter xml;

    public DomConverter() {
        xml = new XmlConverter();
    }

    @Converter(order = 1)
    public String toString(NodeList nodeList, Exchange exchange) throws TransformerException {
        // converting NodeList to String is more tricky
        // sometimes the NodeList is a Node which we can then leverage
        // the XML converter to turn into XML incl. tags

        StringBuilder buffer = new StringBuilder(128);

        // use XML converter at first since it preserves tag names
        boolean found = false;
        if (nodeList instanceof Node node) {
            String s = toString(node, exchange);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(s)) {
                found = true;
                buffer.append(s);
            }
        } else {
            // use XML converter at first since it preserves tag names
            int size = nodeList.getLength();
            for (int i = 0; i < size; i++) {
                Node node = nodeList.item(i);
                String s = toString(node, exchange);
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(s)) {
                    found = true;
                    buffer.append(s);
                }
            }
        }

        // and eventually we must fallback to append without tags, such as when you have
        // used an xpath to select an attribute or text() or something
        if (!found) {
            append(buffer, nodeList);
        }

        return buffer.toString();
    }

    @Converter(order = 2)
    public String toString(Node node, Exchange exchange) throws TransformerException {
        String s;
        if (node instanceof Text textNode) {
            StringBuilder b = new StringBuilder(128);
            b.append(textNode.getNodeValue());
            textNode = (Text) textNode.getNextSibling();
            while (textNode != null) {
                b.append(textNode.getNodeValue());
                textNode = (Text) textNode.getNextSibling();
            }
            s = b.toString();
        } else {
            s = xml.toString(new DOMSource(node), exchange);
        }
        return s;
    }

    @Converter(order = 3)
    public static Integer toInteger(NodeList nodeList) {
        StringBuilder buffer = new StringBuilder(128);
        append(buffer, nodeList);
        String s = buffer.toString();
        return Integer.valueOf(s);
    }

    @Converter(order = 4)
    public static Long toLong(NodeList nodeList) {
        StringBuilder buffer = new StringBuilder(128);
        append(buffer, nodeList);
        String s = buffer.toString();
        return Long.valueOf(s);
    }

    @Converter(order = 5)
    public static List<?> toList(NodeList nodeList) {
        List<Object> answer = new ArrayList<>();
        Iterator<?> it = ObjectHelper.createIterator(nodeList);
        while (it.hasNext()) {
            answer.add(it.next());
        }
        return answer;
    }

    @Converter(order = 6)
    public InputStream toInputStream(NodeList nodeList, Exchange exchange)
            throws TransformerException, UnsupportedEncodingException {
        return new ByteArrayInputStream(toByteArray(nodeList, exchange));
    }

    @Converter(order = 7)
    public byte[] toByteArray(NodeList nodeList, Exchange exchange) throws TransformerException, UnsupportedEncodingException {
        String data = toString(nodeList, exchange);
        return data.getBytes(ExchangeHelper.getCharset(exchange));
    }

    private static void append(StringBuilder buffer, NodeList nodeList) {
        int size = nodeList.getLength();
        for (int i = 0; i < size; i++) {
            append(buffer, nodeList.item(i));
        }
    }

    private static void append(StringBuilder buffer, Node node) {
        if (node instanceof Text text) {
            buffer.append(text.getTextContent());
        } else if (node instanceof Attr attribute) {
            buffer.append(attribute.getTextContent());
        } else if (node instanceof Element element) {
            append(buffer, element.getChildNodes());
        }
    }
}
