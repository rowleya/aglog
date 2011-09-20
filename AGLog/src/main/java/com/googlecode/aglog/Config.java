/*
 * @(#)ConfigParser.java
 * Created: 12-Jan-2007
 * Version: 1.0
 * Copyright (c) 2005-2006, University of Manchester All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials
 * provided with the distribution. Neither the name of the University of
 * Manchester nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.googlecode.aglog;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Parses the config file
 *
 * @author Andrew G D Rowley
 * @version 1.0
 */
public class Config extends DefaultHandler {

    // The start of an end tag
    private static final String TAG_END_START = "</";

    // The end of a tag
    private static final String TAG_END = ">";

    // The start of a start tag
    private static final String TAG_START = "<";

    // The parameter name attribute
    private static final String NAME_ATTRIBUTE = "name";

    // The parameter tag
    private static final String PARAM_TAG = "param";

    // The not allowed error
    private static final String NOT_ALLOWED_ERROR =
        " not allowed at this point";

    // The xml parser to use
    private static final String XML_PARSER_IMPLEMENTATION =
        "org.apache.xerces.parsers.SAXParser";

    // The reader of the XML
    private XMLReader parser = null;

    // True if we are in the service tag in the document
    private boolean inService = false;

    // True if we are in the param tag in the document
    private boolean inParam = false;

    // The name of the current parameter
    private String currentParamName = null;

    // The value of the current parameter
    private String currentParamValue = null;

    // The parameters of the current service
    private HashMap<String, Vector<String>> currentParameters = null;

    // The name of the root tag
    private String rootTag = null;

    /**
     * Parses a config file
     * @param file The config file
     * @param rootTag The root tag
     * @throws SAXException
     * @throws IOException
     */
    public Config(String file, String rootTag) throws SAXException,
            IOException {
        this.rootTag = rootTag;
        FileReader reader = new FileReader(file);
        InputSource source = new InputSource(reader);
        parser = XMLReaderFactory.createXMLReader(XML_PARSER_IMPLEMENTATION);
        parser.setContentHandler(this);
        parser.parse(source);
    }

    /**
     *
     * @see org.xml.sax.ContentHandler#startElement(java.lang.String,
     *     java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    public void startElement(String namespaceURI, String localName,
                String qualifiedName, Attributes atts) throws SAXException {
        if (!inService) {
            if (localName.equals(rootTag)) {
                inService = true;
                currentParameters = new HashMap<String, Vector<String>>();
            } else {
                throw new SAXException(localName
                        + NOT_ALLOWED_ERROR);
            }
        } else if (inService) {
            if (localName.equals(PARAM_TAG)) {
                currentParamName = atts.getValue("", NAME_ATTRIBUTE);
                if (currentParamName == null) {
                    throw new SAXException("Required Attribute "
                            + NAME_ATTRIBUTE
                            + " missing from parameter");
                }
                inParam = true;
                currentParamValue = "";
            } else {
                throw new SAXException(localName + NOT_ALLOWED_ERROR);
            }
        } else {
            throw new SAXException(localName + NOT_ALLOWED_ERROR);
        }
    }

    /**
     *
     * @see org.xml.sax.ContentHandler#endElement(java.lang.String,
     *     java.lang.String, java.lang.String)
     */
    public void endElement(String namespaceURI, String localName,
            String qualifiedName) throws SAXException {
        if (inService && localName.equals(rootTag)) {
            inService = false;
        } else if (inParam && localName.equals(PARAM_TAG)) {
            if (currentParameters.containsKey(currentParamName)) {
                Vector<String> values = currentParameters.get(currentParamName);
                values.add(currentParamValue);
                currentParameters.put(currentParamName, values);
            } else {
                Vector<String> values = new Vector<String>();
                values.add(currentParamValue);
                currentParameters.put(currentParamName, values);
            }
            inParam = false;
        } else {
            throw new SAXException(localName
                    + " ended but did not start");
        }
    }

    /**
     *
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    public void characters(char[] ch, int offset, int length) {
        if (inParam) {
            currentParamValue += new String(ch, offset, length);
        }
    }

    /**
     * Gets a loaded parameter
     * @param name The name of the parameter
     * @param def The default value
     * @return The parameter, or the default value if not specified
     */
    public String getParameter(String name, String def) {
        Vector<String> values = currentParameters.get(name);
        if (values == null) {
            return def;
        }
        return (String) values.get(0);
    }

    /**
     * Gets all the parameters with the given name
     * @param name The name of the parameter
     * @return An array of strings (zero length if parameter doesn't exist)
     */
    public String[] getParameters(String name) {
        Vector<String> values = currentParameters.get(name);
        if (values == null) {
            return new String[0];
        }
        return (String []) values.toArray(new String[0]);
    }

    /**
     * Returns an integer parameter
     * @param name The name of the parameter
     * @param def The default value
     * @return The parameter value as an integer
     */
    public int getIntegerParameter(String name, int def) {
        return Integer.parseInt(getParameter(name, String.valueOf(def)));
    }

    /**
     * Sets a parameter
     * @param name The name of the parameter
     * @param value The value of a parameter
     */
    public void setParameter(String name, String value) {
        Vector<String> values = new Vector<String>();
        values.add(value);
        currentParameters.put(name, values);
    }

    /**
     * Saves the parameters to a file
     * @param filename The name of the file
     * @throws IOException
     */
    public void saveParameters(String filename) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));
        Iterator<String> iterator = currentParameters.keySet().iterator();
        writer.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        writer.println(TAG_START + rootTag + TAG_END);
        while (iterator.hasNext()) {
            String name = iterator.next();
            Vector<String> values = currentParameters.get(name);
            for (int i = 0; i < values.size(); i++) {
                String value = (String) values.get(i);
                writer.print(TAG_START + PARAM_TAG + " " + NAME_ATTRIBUTE
                        + "=\"" + name + "\"");
                writer.print(value);
                writer.println(TAG_END_START + PARAM_TAG + TAG_END);
            }
        }
        writer.println(TAG_END_START + rootTag + TAG_END);
    }
}
