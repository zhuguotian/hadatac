package org.hadatac.entity.pojo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.hadatac.console.http.SPARQLUtils;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.NameSpaces;

import com.typesafe.config.ConfigFactory;

public class InstrumentType extends HADatAcClass implements Comparable<InstrumentType> {

	static String className = "vstoi:Instrument";
	
	private String url;

	public InstrumentType () {
		super(className);
	}

    public String getURL() {
        return url;
    }

    public void setURL(String url) {
        this.url = url;
    }
    
    public String getSuperLabel() {
    	InstrumentType superInsType = InstrumentType.find(getSuperUri());
    	if (superInsType == null || superInsType.getLabel() == null) {
    		return "";
    	}
    	return superInsType.getLabel();
    }


	public static List<InstrumentType> find() {
		List<InstrumentType> instrumentTypes = new ArrayList<InstrumentType>();
		String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() +
				" SELECT ?uri WHERE { " +
				" ?uri rdfs:subClassOf* " + className + " . " + 
				"} ";

		ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

		while (resultsrw.hasNext()) {
			QuerySolution soln = resultsrw.next();
			InstrumentType instrumentType = find(soln.getResource("uri").getURI());
			instrumentTypes.add(instrumentType);
		}			

		java.util.Collections.sort((List<InstrumentType>) instrumentTypes);
		return instrumentTypes;

	}

	public static Map<String,String> getMap() {
		List<InstrumentType> list = find();
		Map<String,String> map = new HashMap<String,String>();
		for (InstrumentType typ: list) 
			map.put(typ.getUri(),typ.getLabel());
		return map;
	}

	public static InstrumentType find(String uri) {
		InstrumentType instrumentType = null;
		Model model;
		Statement statement;
		RDFNode object;

		String queryString = "DESCRIBE <" + uri + ">";
		Query query = QueryFactory.create(queryString);
		QueryExecution qexec = QueryExecutionFactory.sparqlService(
		        CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);
		model = qexec.execDescribe();

		instrumentType = new InstrumentType();
		StmtIterator stmtIterator = model.listStatements();

		while (stmtIterator.hasNext()) {
			statement = stmtIterator.next();
			object = statement.getObject();
			if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#label")) {
				instrumentType.setLabel(object.asLiteral().getString());
			} else if (statement.getPredicate().getURI().equals("http://hadatac.org/ont/vstoi#hasWebDocumentation")) {
				instrumentType.setURL(object.asLiteral().getString());
			} else if (statement.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#subClassOf")) {
				instrumentType.setSuperUri(object.asResource().getURI());
			}
		}
		
		instrumentType.setUri(uri);
		instrumentType.setLocalName(uri.substring(uri.indexOf('#') + 1));

		return instrumentType;
	}

	@Override
	public int compareTo(InstrumentType another) {
		if (this.getLabel() != null && another.getLabel() != null) {
			return this.getLabel().compareTo(another.getLabel());
		}
		return this.getLocalName().compareTo(another.getLocalName());
	}

}
