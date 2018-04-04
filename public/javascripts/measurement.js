var query_res = document.getElementById('query');
var results = query_res.dataset.documents;
var json = JSON.parse(results);
var facet_res = document.getElementById('facetDiv');
var facetsStrFromDiv = facet_res.dataset.documents;
var jsonFacet = JSON.parse(facetsStrFromDiv);

function getURLParameter(name) {
	return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(location.search)||[,""])[1].replace(/\+/g, '%20'))||null
}

function facetPrettyName(type, value) {
    switch(type) {
        case 'study_uri':
            if (value.indexOf(":") > 0) {
	       value = value.substring(value.indexOf(":") + 1);
	    }
            break;
        case 'acquisition_uri':
            if (value.indexOf("#") > 0) {
               value = value.substring(value.indexOf("#") + 1);
            }
            break;
        case 'study_uri,acquisition_uri':
            if (value.indexOf("#") > 0) {
               value = value.substring(value.indexOf("#") + 1);
            } else {
               if (value.indexOf(":") > 0) {
	          value = value.substring(value.indexOf(":") + 1);
	       }
	    }
            break;
        case 'entity':
        	value += "'s attribute";
            break;
    }
    return value;
}

var tree_id = 0;
function create_item(data, selected_elems) {
	if (null == data) {
		return;
	}
	var items = [];
	var children = data.children;
	for (var i_child in children) {
		var element = {};
		element.id = tree_id;
		tree_id++;
		element.text = facetPrettyName(data.field, children[i_child].value) + ' (' + children[i_child].count + ')';
		element.tooltip = children[i_child].tooltip;
		var facet_content = {}
		facet_content["id"] = children[i_child].tooltip;
		facet_content[children[i_child].field] = children[i_child].tooltip;
		element.userdata = [
			{"name": "field", "content": children[i_child].field},
			{"name": "value", "content": children[i_child].tooltip},
			{"name": "self_facet", "content": facet_content}];
		
		if (selected_elems.indexOf(element.tooltip) > -1) {
			element.checked = 1;
		}
		element.item = create_item(children[i_child], selected_elems);
		
		if (element.item.length == 1 && element.item[0].tooltip == element.tooltip) {
			var facet = {};
			facet["id"] = children[i_child].tooltip;
			facet[children[i_child].field] = children[i_child].tooltip;
			for (var i = 0; i < element.item[0].userdata.length; i++) {
				if (element.item[0].userdata[i]["name"] == "self_facet") {
					facet['children'] = [element.item[0].userdata[i]["content"]];
				}
			}
			element.userdata.push({"name": "facet", "content": facet});
			element.item = [];
		}
		
		for (var i = 0; i < element.item.length; i++) {
			if (element.item[i].checked == 1) {
				element.open = "yes";
				break;
			}
		}
		items.push(element);
	}
	
	items.sort(function(a, b) {
		return a.text.localeCompare(b.text);
	});
	
	return items;
}

function parseSolrFacetToTree(facet_name, selected_elems) {
	console.log("facet_name: " + facet_name);
	console.log("selected_elems: " + selected_elems);
	dataTree = {};
	tree_id = 0;
	dataTree.id = tree_id++;
	items = create_item(json.extra_facets[facet_name], selected_elems);
	if (null == items) {
		items = [];
	}
	dataTree.item = items;
	return dataTree;
}

function parseSolrFacetToMergedTree(facet_name, selected_elems) {
	dataTree = {};
	tree_id = 0;
	dataTree.id = tree_id++;
	items = create_merged_item(json.extra_facets[facet_name], selected_elems,
			0, 0, dataTree, [], [], [], []);
	if (null == items) {
		items = [];
	}
	dataTree.item = items;
	return dataTree;
}

function createFacet(facets, i) {
	if (i < facets.length) {
		var pair = new Object();
		var facet = facets[i];
		pair['id'] = facet['value'];
		pair[facet['field']] = facet['value'];
		var child = createFacet(facets, i + 1);
		if (child != null) {
			pair['children'] = [child];
		}
		return pair;
	}
	
	return null;
}

function create_merged_item(data, selected_elems, curLevel, 
		levelToBegin, pivot, text, tooltips, facets, retChildren) {
	if (null == data) {
		return;
	}
	
	var items = [];
	if (data.children.length == 0 && curLevel > levelToBegin) {
		pivot.text = text.join(' ') + ' (' + data.count + ')';
		pivot.tooltip = tooltips.join(' ');
		pivot.userdata.push({"name": "facet", "content": createFacet(facets, 0)});
		retChildren.push(pivot);
	} else {
		var children = data.children;
		for (var i_child in children) {	
			var element = {};
			element.id = tree_id++;
			element.text = children[i_child].value + ' (' + children[i_child].count + ')';
			element.tooltip = '<' + children[i_child].tooltip + '>';
			element.userdata = [
				{"name": "field", "content": children[i_child].field},
				{"name": "value", "content": children[i_child].tooltip}];
			if (selected_elems.indexOf(children[i_child].tooltip) > -1) {
				element.checked = 1;
			}
						
			if (curLevel <= levelToBegin) {
				element.item = create_merged_item(children[i_child], selected_elems,
						curLevel + 1, levelToBegin, element, [], [], facets, retChildren);
				
				if (curLevel == levelToBegin) {
					element.item = [];
					for (var i in retChildren) {
						element.item.push(retChildren[i]);
					}
					retChildren.length = 0;
				}
				
				for (var i = 0; i < element.item.length; i++) {
					if (element.item[i].checked == 1) {
						element.open = "yes";
						break;
					}
				}
				
				items.push(element);
			} else {
				if (children[i_child].value.length > 0) {
					text.push(children[i_child].value);
				}
				if (children[i_child].tooltip.length > 0) {
					tooltips.push('<' + children[i_child].tooltip + '>');
				}
				facets.push({
					"field": children[i_child].field, 
					"value": children[i_child].tooltip});
				
				create_merged_item(children[i_child], selected_elems,
						curLevel + 1, levelToBegin, element, text, tooltips, facets, retChildren);
				
				if (children[i_child].value.length > 0) {
					text.pop();
				}
				if (children[i_child].tooltip.length > 0) {
					tooltips.pop();
				}
				facets.pop();
			}
		}
	}
	
	items.sort(function(a, b) {
		return a.text.localeCompare(b.text);
	});
	
	return items;
}

