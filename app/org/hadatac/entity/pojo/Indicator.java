package org.hadatac.entity.pojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.WordUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.hadatac.console.controllers.metadata.DynamicFunctions;
import org.hadatac.console.http.SPARQLUtils;
import org.hadatac.console.models.Facet;
import org.hadatac.console.models.FacetHandler;
import org.hadatac.console.models.Facetable;
import org.hadatac.metadata.loader.LabkeyDataHandler;
import org.hadatac.metadata.loader.URIUtils;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.NameSpaces;
import org.labkey.remoteapi.CommandException;


public class Indicator extends HADatAcThing implements Comparable<Indicator> {

    public static String INSERT_LINE1 = "INSERT DATA {  ";
    public static String DELETE_LINE1 = "DELETE WHERE {  ";
    public static String DELETE_LINE3 = " ?p ?o . ";
    public static String LINE_LAST = "}  ";

    private String uri;
    private String label;
    private String comment;
    private String superUri;

    static String className = "hasco:Indicator";

    public Indicator() {
        setUri("");
        setSuperUri("hasco:Indicator");
        setLabel("");
        setComment("");
    }

    public Indicator(String uri) {
        setUri(uri);
        setSuperUri("hasco:Indicator");
        setLabel("");
        setComment("");
    }

    public Indicator(String uri, String label, String comment) {
        setUri(uri);
        setSuperUri("hasco:Indicator");
        setLabel(label);
        setComment(comment);
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getSuperUri() {
        return superUri;
    }
    public void setSuperUri(String superUri) {
        this.superUri = superUri;
    }

    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

    public String getComment() {
        return comment;
    }
    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if((o instanceof Indicator) && (((Indicator)o).getUri().equals(this.getUri()))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return getUri().hashCode();
    }

    private static List<Indicator> findImmediateSubclasses(String indicatorUri, boolean justSub) {
        List<Indicator> indicators = new ArrayList<Indicator>();
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() +
                " SELECT ?uri WHERE { " +
                " ?uri rdfs:subClassOf " + indicatorUri + " . " + 
                "} ";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            Indicator indicator = find(soln.getResource("uri").getURI());
            indicator.setSuperUri(indicatorUri);
            if (!indicatorUri.equals(className) || (indicatorUri.equals(className) && !justSub)) {
            	indicators.add(indicator);
            } 
            if (indicatorUri.equals(className)) {
            	indicators.addAll(Indicator.findImmediateSubclasses(URIUtils.replaceNameSpace(indicator.getUri()), justSub));
            }
        }			

        java.util.Collections.sort((List<Indicator>) indicators);
        return indicators;		
    }

    public static List<Indicator> find() {
    	return Indicator.findImmediateSubclasses(className, false);
    }

    public static List<Indicator> findSubClasses() {
    	return Indicator.findImmediateSubclasses(className, true);
    }

    public static Indicator find(String uri) {
        Indicator indicator = null;
        Model model;
        Statement statement;
        RDFNode object;

        String queryString = "DESCRIBE <" + uri + ">";
        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);
        model = qexec.execDescribe();

        indicator = new Indicator();
        StmtIterator stmtIterator = model.listStatements();

        while (stmtIterator.hasNext()) {
            statement = stmtIterator.next();
            object = statement.getObject();
            if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#label")) {
                indicator.setLabel(object.asLiteral().getString());
            }
            if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#comment")) {
                indicator.setComment(object.asLiteral().getString());
            }
        }

        indicator.setUri(uri);

        return indicator;
    }

    public static List<Indicator> findRecursive() {
        List<Indicator> indicators = new ArrayList<Indicator>();
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() +
                " SELECT ?uri WHERE { " +
                " ?uri rdfs:subClassOf hasco:Indicator+ . " + 
                "} ";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            Indicator indicator = find(soln.getResource("uri").getURI());
            indicators.add(indicator);
        }			

        java.util.Collections.sort((List<Indicator>) indicators);
        return indicators;		
    }

    public static Map<String, Map<String,String>> getValuesAndLabels(Map<String, String> indicatorMap) {
        Map<String, Map<String,String>> indicatorValueMap = new HashMap<String, Map<String,String>>();
        Map<String,String> values = new HashMap<String, String>();
        String indicatorValue = "";
        String indicatorValueLabel = "";
        for (Map.Entry<String, String> entry : indicatorMap.entrySet()) {
            values = new HashMap<String, String>();
            String indicatorType = entry.getKey().toString();
            String indvIndicatorQuery = 
            		NameSpaces.getInstance().printSparqlNameSpaceList() +
            		" SELECT DISTINCT ?indicator " +
                    " (MIN(?label_) AS ?label)" +
                    " WHERE { ?indicator rdfs:subClassOf " + indicatorType + " . " +
                    "   ?indicator rdfs:label ?label_ . " + 
                    " } GROUP BY ?indicator ?label_"; 
            try {				
                ResultSetRewindable resultsrwIndvInd = SPARQLUtils.select(
                        CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), indvIndicatorQuery);

                while (resultsrwIndvInd.hasNext()) {
                    QuerySolution soln = resultsrwIndvInd.next();
                    indicatorValueLabel = "";
                    if (soln.contains("label")){
                        indicatorValueLabel = soln.get("label").toString();
                    }
                    else {
                        System.out.println("getIndicatorValues() No Label: " + soln.toString() + "\n");
                    }
                    if (soln.contains("indicator")){
                        indicatorValue = URIUtils.replaceNameSpaceEx(soln.get("indicator").toString());
                        values.put(indicatorValue,indicatorValueLabel);
                    }
                }
                indicatorValueMap.put(indicatorType,values);
            } catch (QueryExceptionHTTP e) {
                e.printStackTrace();
            }
        }
        return indicatorValueMap;
    }

    public static Map<String, List<String>> getValuesJustLabels(Map<String, String> indicatorMap){

        Map<String, List<String>> indicatorValueMap = new HashMap<String, List<String>>();
        List<String> values = new ArrayList<String>();
        String indicatorValueLabel = "";
        for(Map.Entry<String, String> entry : indicatorMap.entrySet()){
            values = new ArrayList<String>();
            String indicatorType = entry.getKey().toString();
            String indvIndicatorQuery = 
            		NameSpaces.getInstance().printSparqlNameSpaceList() 
                    + " SELECT DISTINCT ?indicator (MIN(?label_) AS ?label) WHERE { "
                    + " ?indicator rdfs:subClassOf " + indicatorType + " . "
                    + " ?indicator rdfs:label ?label_ . "
                    + " } "
                    + " GROUP BY ?indicator ?label_";
            try {				
                ResultSetRewindable resultsrwIndvInd = SPARQLUtils.select(
                        CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), indvIndicatorQuery);

                while (resultsrwIndvInd.hasNext()) {
                    QuerySolution soln = resultsrwIndvInd.next();
                    indicatorValueLabel = "";
                    if (soln.contains("label")){
                        indicatorValueLabel = soln.get("label").toString();
                    }
                    else {
                        System.out.println("getIndicatorValues() No Label: " + soln.toString() + "\n");
                    }
                    if (soln.contains("indicator")){
                        values.add(indicatorValueLabel);
                    }
                }
                String indicatorTypeLabel = entry.getValue().toString();
                indicatorValueMap.put(indicatorTypeLabel,values);
            } catch (QueryExceptionHTTP e) {
                e.printStackTrace();
            }
        }
        return indicatorValueMap;
    }

    public static Map<String, List<String>> getValues(Map<String, String> indicatorMap) {
        Map<String, List<String>> indicatorValueMap = new HashMap<String, List<String>>();
        List<String> values = new ArrayList<String>();
        String indicatorValueLabel = "";
        for(Map.Entry<String, String> entry : indicatorMap.entrySet()){
            values = new ArrayList<String>();
            String indicatorType = entry.getKey().toString();
            String indvIndicatorQuery = NameSpaces.getInstance().printSparqlNameSpaceList() + 
            		" SELECT DISTINCT ?indicator " +
                    "(MIN(?label_) AS ?label)" +
                    "WHERE { ?indicator rdfs:subClassOf " + indicatorType + " . " +
                    "?indicator rdfs:label ?label_ . " + 
                    "} GROUP BY ?indicator ?label";
            try {				
                ResultSetRewindable resultsrwIndvInd = SPARQLUtils.select(
                        CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), indvIndicatorQuery);

                while (resultsrwIndvInd.hasNext()) {
                    QuerySolution soln = resultsrwIndvInd.next();
                    if (soln.contains("label")){
                        indicatorValueLabel = URIUtils.replaceNameSpaceEx(soln.get("indicator").toString());
                        values.add(indicatorValueLabel);
                    }
                    else {
                        System.out.println("getIndicatorValues() No Label: " + soln.toString() + "\n");
                    }
                }
                indicatorValueMap.put(indicatorType,values);
            } catch (QueryExceptionHTTP e) {
                e.printStackTrace();
            }
        }
        return indicatorValueMap;
    }	

    public static List<Indicator> findStudyIndicators() {
        List<Indicator> indicators = new ArrayList<Indicator>();
        String query = NameSpaces.getInstance().printSparqlNameSpaceList() 
                + " SELECT DISTINCT ?indicator ?indicatorLabel ?indicatorComment WHERE { "
                + " ?subTypeUri rdfs:subClassOf* hasco:Study . "
                + " ?studyUri a ?subTypeUri . "
                + " ?dataAcq hasco:isDataAcquisitionOf ?studyUri ."
                + " ?dataAcq hasco:hasSchema ?schemaUri ."
                + " ?schemaAttribute hasco:partOfSchema ?schemaUri . "
                + " ?schemaAttribute hasco:hasAttribute ?attribute . "
                + " {  { ?indicator rdfs:subClassOf hasco:StudyIndicator } UNION { ?indicator rdfs:subClassOf hasco:SampleIndicator } } . "
                + " ?indicator rdfs:label ?indicatorLabel . " 
                + " OPTIONAL { ?indicator rdfs:comment ?indicatorComment } . "
                + " ?attribute rdfs:subClassOf+ ?indicator . " 
                + " ?attribute rdfs:label ?attributeLabel . "
                + " }";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);

        Indicator indicator = null;
        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            indicator = new Indicator();
            indicator.setUri(soln.getResource("indicator").getURI());
            indicator.setLabel(soln.get("indicatorLabel").toString());
            if(soln.contains("indicatorComment")){
                indicator.setComment(soln.get("indicatorComment").toString());
            }
            indicators.add(indicator);
        }

        java.util.Collections.sort(indicators);
        return indicators; 
    }
    
    @Override
    public Map<Facetable, List<Facetable>> getTargetFacets(
            Facet facet, FacetHandler facetHandler) {
        return getTargetFacetsFromTripleStore(facet, facetHandler);
    }

    @Override
    public Map<Facetable, List<Facetable>> getTargetFacetsFromTripleStore(
            Facet facet, FacetHandler facetHandler) {

        String valueConstraint = "";
        if (!facet.getFacetValuesByField("indicator_uri_str").isEmpty()) {
            valueConstraint += " VALUES ?indicator { " + stringify(
                    facet.getFacetValuesByField("indicator_uri_str")) + " } \n ";
        }

        if (!facet.getFacetValuesByField("characteristic_uri_str_multi").isEmpty()) {
            valueConstraint += " VALUES ?attributeUri { " + stringify(
                    facet.getFacetValuesByField("characteristic_uri_str_multi")) + " } \n ";
        }

        if (!facet.getFacetValuesByField("dasa_uri_str").isEmpty()) {
            valueConstraint += " VALUES ?schemaAttribute { " + stringify(
                    facet.getFacetValuesByField("dasa_uri_str")) + " } \n ";
        }

        String query = "";
        query += NameSpaces.getInstance().printSparqlNameSpaceList();
        query += "SELECT ?indicator ?dataAcq ?schemaAttribute ?attributeUri ?attributeLabel WHERE { \n"
                + valueConstraint + " \n"
                + "?subTypeUri rdfs:subClassOf* hasco:Study . \n"
                + "?studyUri a ?subTypeUri . \n"
                + "?dataAcq hasco:isDataAcquisitionOf ?studyUri . \n"
                + "?dataAcq hasco:hasSchema ?schemaUri . \n"
                + "?schemaAttribute hasco:partOfSchema ?schemaUri . \n"
                + "?schemaAttribute hasco:hasAttribute ?attributeUri . \n" 
                + "?attributeUri rdfs:subClassOf* ?indicator . \n"
                + "?attributeUri rdfs:label ?attributeLabel . \n"
                + " { ?indicator rdfs:subClassOf hasco:SampleIndicator } UNION { ?indicator rdfs:subClassOf hasco:StudyIndicator } . \n"
                + "}";

        // System.out.println("Indicator query: \n" + query);

        Map<Facetable, List<Facetable>> mapIndicatorToCharList = new HashMap<Facetable, List<Facetable>>();
        try {
            ResultSetRewindable resultsrw = SPARQLUtils.select(
                    CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);

            while (resultsrw.hasNext()) {
                QuerySolution soln = resultsrw.next();
                Indicator indicator = new Indicator();
                indicator.setUri(soln.get("indicator").toString());
                indicator.setLabel(WordUtils.capitalize(HADatAcThing.getShortestLabel(soln.get("indicator").toString())));
                indicator.setField("indicator_uri_str");
                indicator.setQuery(query);

                AttributeInstance attrib = new AttributeInstance();
                attrib.setUri(soln.get("attributeUri").toString());
                attrib.setLabel(WordUtils.capitalize(soln.get("attributeLabel").toString()));
                attrib.setField("characteristic_uri_str");

                if (!mapIndicatorToCharList.containsKey(indicator)) {
                    List<Facetable> attributes = new ArrayList<Facetable>();
                    mapIndicatorToCharList.put(indicator, attributes);
                }
                if (!mapIndicatorToCharList.get(indicator).contains(attrib)) {
                    mapIndicatorToCharList.get(indicator).add(attrib);
                }

                Facet subFacet = facet.getChildById(indicator.getUri());
                subFacet.putFacet("indicator_uri_str", indicator.getUri());
                subFacet.putFacet("dasa_uri_str", soln.get("schemaAttribute").toString());
            }
        } catch (QueryExceptionHTTP e) {
            e.printStackTrace();
        }

        return mapIndicatorToCharList;
    }

    @Override
    public int saveToLabKey(String user_name, String password) {
        LabkeyDataHandler loader = LabkeyDataHandler.createDefault(user_name, password);
        List< Map<String, Object> > rows = new ArrayList< Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("hasURI", URIUtils.replaceNameSpaceEx(getUri()));
        row.put("rdfs:subClassOf", "hasco:Indicator");
        row.put("rdfs:label", getLabel());
        row.put("rdfs:comment", getComment());
        rows.add(row);

        int totalChanged = 0;
        try {
            totalChanged = loader.insertRows("IndicatorType", rows);
        } catch (CommandException e) {
            try {
                totalChanged = loader.updateRows("IndicatorType", rows);
            } catch (CommandException e2) {
                System.out.println("[ERROR] Could not insert or update Indicator(s)");
            }
        }
        return totalChanged;
    }

    @Override
    public int deleteFromLabKey(String user_name, String password) {
        LabkeyDataHandler loader = LabkeyDataHandler.createDefault(user_name, password);
        List< Map<String, Object> > rows = new ArrayList< Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("hasURI", URIUtils.replaceNameSpaceEx(getUri().replace("<","").replace(">","")));
        rows.add(row);
        for (Map<String,Object> r : rows) {
            System.out.println("deleting Indicator " + r.get("hasURI"));
        }

        try {
            return loader.deleteRows("IndicatorType", rows);
        } catch (CommandException e) {
            System.out.println("[ERROR] Could not delete Indicator(s)");
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int compareTo(Indicator another) {
    	String from = this.getSuperUri() + this.getLabel();
    	String to = another.getSuperUri() + another.getLabel();
        return from.compareTo(to);
    }

    @Override
    public boolean saveToTripleStore() {
        if (uri == null || uri.equals("")) {
            System.out.println("[ERROR] Trying to save Indicator without assigning a URI");
            return false;
        }

        deleteFromTripleStore();

        String insert = "";
        String ind_uri = "";

        System.out.println("Indicator.save(): Checking URI");
        if (this.getUri().startsWith("<")) {
            ind_uri = this.getUri();
        } else {
            ind_uri = "<" + this.getUri() + ">";
        }
        insert += NameSpaces.getInstance().printSparqlNameSpaceList();
        insert += INSERT_LINE1;

        if (!getNamedGraph().isEmpty()) {
            insert += " GRAPH <" + getNamedGraph() + "> { ";
        }

        if (label != null && !label.equals("")) {
            insert += ind_uri + " rdfs:label \"" + label + "\" .  ";
        }
        if (comment != null && !comment.equals("")) {
            insert += ind_uri + " rdfs:comment \"" + comment + "\" .  ";
        }
        if (superUri != null && !superUri.equals("")) {
            insert += ind_uri + " rdfs:subClassOf <" + DynamicFunctions.replacePrefixWithURL(superUri) + "> .  ";
        }

        if (!getNamedGraph().isEmpty()) {
            insert += " } ";
        }

        insert += LINE_LAST;

        try {
            UpdateRequest request = UpdateFactory.create(insert);
            UpdateProcessor processor = UpdateExecutionFactory.createRemote(
                    request, CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_UPDATE));
            processor.execute();
        } catch (QueryParseException e) {
            System.out.println("QueryParseException due to update query: " + insert);
            throw e;
        }

        return true;
    }

    @Override
    public boolean saveToSolr() {
        return false;
    }

    @Override
    public int deleteFromSolr() {
        return 0;
    }
}
