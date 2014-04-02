package hw2;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

//JAXP packages

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.*;

import com.sun.org.apache.xml.internal.resolver.readers.SAXParserHandler;

//import javax.xml.parsers.*;
//import org.xml.sax.*;
//import java.io.*;


/**
 * 
 * @author raphaelas
 *
 */
public class OSMParser extends DefaultHandler implements IOSMParser{
    /**
	 * @author raphaelas
	 *
	 */
	public class WayHandler extends DefaultHandler {
		//TODO: Make this its own class independent of OSMParser.
		//This class handles parsing of Way child nodes.
		private OSMParser parentParser;
	    private XMLReader theReader;


		WayHandler(XMLReader x, OSMParser o) {
			parentParser = o;
			theReader = x;
		}
		
	    public void startElement(String uri, String localName, String qName,
	    		Attributes attributes) throws SAXException {
	    	if (qName.equals("nd")) {
		    	String refValue = attributes.getValue(0); //The 'nd ref' value.
		    	parentParser.addRef(refValue);
	    	}
	    }
	    
	    public void endElement(String uri, String localName, String qName)
	            throws SAXException {
	        if (qName.equals("way")) {
	        	parentParser.determineClosedWay();
	            theReader.setContentHandler(parentParser);
	        }
	    }
	   
	}
	
	public OSMParser(XMLReader r) {
		xmlReader = r;
	}

	/** Constants used for JAXP 1.2 */
    static final String JAXP_SCHEMA_LANGUAGE =
        "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String W3C_XML_SCHEMA =
        "http://www.w3.org/2001/XMLSchema";
    static final String JAXP_SCHEMA_SOURCE =
        "http://java.sun.com/xml/jaxp/properties/schemaSource";

    //private JSONArray theArray;
    private String id;
    private String name;
    private String website;
    private String wiki;
    private int numNodes;
    private ArrayList<String> users;
    private HashMap<String, String[]> nodeMap;
    
    boolean inWay = false;
    private XMLReader xmlReader;
    private ArrayList<String> elementRefs;

    
    
    public void startDocument() throws SAXException {
    	elementRefs = new ArrayList<String>();
    	nodeMap = new HashMap<String, String[]>(1000000);
    }
    
    // Parser calls this for each element in a document
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts)
	throws SAXException
    {
    	if (qName.equals("node")) {
    		String nodeID = atts.getValue("id"); //nodeID
    		String[] nodeAttsArray = new String[3];
    		nodeAttsArray[0] = atts.getValue("lat"); //nodeLatitude
    		nodeAttsArray[1] = atts.getValue("lon"); //nodeLongitude
    		nodeAttsArray[2] = atts.getValue("user"); //nodeUser
    		nodeMap.put(nodeID, nodeAttsArray);
    	}
    	else if (qName.equals("way")) {
    		inWay = true;
		    users = new ArrayList<String>();
    		for (int i = 0; i < atts.getLength(); i++) {
    			String currAtts = atts.getLocalName(i);
    			if (currAtts.equals("id")) {
    				id = atts.getValue(i);
    			}
	    		else if (currAtts.equals("name")) {
	    			name = atts.getValue(i);
	    		}
	    		else if (currAtts.equals("website")) {
	    			website = atts.getValue(i);
	    		}
	    		else if (currAtts.equals("wiki")) {
	    			wiki = atts.getValue(i);
	    		}
	    		else if (currAtts.equals("user")) {
	    	    	users.add(atts.getValue(i));
	    		}
	    	}
		    xmlReader.setContentHandler(new WayHandler(xmlReader, this));
    	}
    }
    
    public void addRef(String refToAdd) {
    	elementRefs.add(refToAdd);
    }
    
    private void determineClosedWay() {
    	/*Determines if a recently parsed way is a closed way or not.
    	A closed way is a way that begins and ends at the same coordinates.
    	TODO: This method does not check if node's with different ID's might
    	have the same coordinates - and really be a closed way when my method
    	thinks its an open way. */
    	int theSize = elementRefs.size();
    	if (theSize > 0 && elementRefs.get(0).equals(elementRefs.get(theSize -  1))) {
    	//If this is a closed way:
    		System.out.println("Closed way!");
    		uniqueifyNodes();
    		String[][] wayCoordinates = getWayCoordinates();
    		String[][] uniqueWayCoordinates = uniqueifyCoordinates(wayCoordinates);
    		//Temporary experimental JSON object.  TODO for David is to create the real JSON array.
    		JSONObject jobj = new JSONObject();
	    	jobj.put("id", id);
	    	if (name != null) jobj.put("name", name);
	    	if (website != null) jobj.put("website", website);
	    	if (wiki != null) jobj.put("wiki", wiki);
	    	jobj.put("numNodes", numNodes);
	    	jobj.put("coordinates", uniqueWayCoordinates);
	    	jobj.put("users", users);
	    	System.out.println(jobj);
    	}
    	elementRefs.clear();
	}

	// Parser calls this once after parsing a document
    public void endDocument() throws SAXException {
    	System.out.println("All done!");
    }
  
    public void uniqueifyNodes() {
    	//Removes nodes with duplicate IDs from the elementRefs ArrayList.
    	HashSet<String> hs = new HashSet<String>();
    	hs.addAll(elementRefs);
    	elementRefs.clear();
    	elementRefs.addAll(hs);
    	elementRefs.trimToSize();
    	numNodes += elementRefs.size();
    }
    
    public String[][] getWayCoordinates() {
    	/*Gets a way's coordinates from its corresponding node.  The corresponding
    	 * node is the node that has an 'nd ref' defined as one of the way's children.
    	 Also adds contributing users to the 'users' array. */
    	String[][] theCoords = new String[elementRefs.size()][1];
    	for (int i = 0; i < elementRefs.size(); i++) {
    		theCoords[i][0] = nodeMap.get(elementRefs.get(i))[0]; //nodeLatitude
    		theCoords[i][1] = nodeMap.get(elementRefs.get(i))[1]; //nodeLongitude
    		users.add(nodeMap.get(elementRefs.get(i))[2]); //nodeUser
    	}
    	return theCoords;
    }
    
    public String[][] uniqueifyCoordinates(String[][] wayCoords) {
    	// Removes duplicate coordinate pairs from the 'wayCoordinates' 2D array.
    	HashSet<String[]> keys = new HashSet<String[]>();
    	for (int i = 0; i < wayCoords.length; i++) {
    		keys.add(wayCoords[i]);
    	}
    	
    	return (String[][]) keys.toArray();
/*
		This dead code should soon be deleted:
    	Iterator<String> keyIter = keys.iterator();

    	while (keyIter.hasNext()) {
    	    String key = keyIter.next();
    	    String[] value = nodeMap.get(key);
    	    reversedMap.put(value, key);    	    
    	}
	    Set<String[]> uniqueKeys = reversedMap.keySet();
	    return uniqueKeys;
	    //String[] uniqueKeyArray = (String[]) uniqueKeys.toArray();
*/
    	
    }
    
    
  public JSONArray parse(String osmFile, ITagsRequired tagsRequired) {
	  //The central parse method.  Takes the big 2.5 GB OSM file and a
	  //class that implements the ITagsRequired interface.
	  
        // Create a JAXP SAXParserFactory and configure it
        SAXParserFactory spf = SAXParserFactory.newInstance();

        // Set namespaceAware to true to get a parser that corresponds to
        // the default SAX2 namespace feature setting.  This is necessary
        // because the default value from JAXP 1.0 was defined to be false.
        
        spf.setNamespaceAware(true);

        // Create a JAXP SAXParser
        SAXParser saxParser = null;
		try {
			saxParser = spf.newSAXParser();
		} catch (Exception e) {
			System.err.println("Exception on create saxParser.");
		}
      
        // Get the encapsulated SAX XMLReader
        xmlReader = null;
		try {
			xmlReader = saxParser.getXMLReader();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        // Set the ContentHandler of the XMLReader
        xmlReader.setContentHandler(new OSMParser(xmlReader));

        // Set an ErrorHandler before parsing
        xmlReader.setErrorHandler(new MyErrorHandler(System.err));

        // Tell the XMLReader to parse the XML document
        
        try {
			xmlReader.parse(osmFile);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
	    return null;
  }
  
  public static void main(String[] args) {
	  //The main method.  All it does is start the parser.
	  OSMParser theWorkingParser = new OSMParser(null);
	  TagsMap theMap = new TagsMap();
	  theWorkingParser.parse("./lib/new-york-latest-full.osm", theMap);
  }
  
  // Error handler to report errors and warnings
  private static class MyErrorHandler implements ErrorHandler {
      /** Error handler output goes here */
      private PrintStream out;

      MyErrorHandler(PrintStream out) {
          this.out = out;
      }

      /**
       * Returns a string describing parse exception details
       */
      private String getParseExceptionInfo(SAXParseException spe) {
          String systemId = spe.getSystemId();
          if (systemId == null) {
              systemId = "null";
          }
          String info = "URI=" + systemId +
              " Line=" + spe.getLineNumber() +
              ": " + spe.getMessage();
          return info;
      }

      // The following methods are standard SAX ErrorHandler methods.
      // See SAX documentation for more info.

      public void warning(SAXParseException spe) throws SAXException {
          out.println("Warning: " + getParseExceptionInfo(spe));
      }
      
      public void error(SAXParseException spe) throws SAXException {
          String message = "Error: " + getParseExceptionInfo(spe);
          throw new SAXException(message);
      }

      public void fatalError(SAXParseException spe) throws SAXException {
          String message = "Fatal Error: " + getParseExceptionInfo(spe);
          throw new SAXException(message);
      }
  }
  
}