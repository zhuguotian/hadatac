/* oss.sheetjs.com (C) 2014-present SheetJS -- http://sheetjs.com */
/* vim: set ts=2: */

var demo_enabled = true;
var sdd_suggestions;

/** drop target **/
var _target = document.getElementById('drop');
var _file = document.getElementById('file');
var _grid = document.getElementById('grid');

/** Spinner **/
var spinner;

var _workstart = function() { spinner = new Spinner().spin(_target); }
var _workend = function() { spinner.stop(); }

/** Alerts **/
var _badfile = function() {
  alertify.alert('This file does not appear to be a valid Excel file.  If we made a mistake, please send this file to <a href="mailto:dev@sheetjs.com?subject=I+broke+your+stuff">dev@sheetjs.com</a> so we can take a look.', function(){});
};

var _pending = function() {
  alertify.alert('Please wait until the current file is processed.', function(){});
};

var _large = function(len, cb) {
  alertify.confirm("This file is " + len + " bytes and may take a few moments.  Your browser may lock up during this process.  Shall we play?", cb);
};

var _failed = function(e) {
  console.log(e, e.stack);
  alertify.alert('We unfortunately dropped the ball here.  Please test the file using the <a href="/js-xlsx/">raw parser</a>.  If there are issues with the file processor, please send this file to <a href="mailto:dev@sheetjs.com?subject=I+broke+your+stuff">dev@sheetjs.com</a> so we can make things right.', function(){});
};
var dd_url;
function getURL(url){
  dd_url=url;
}
function hideView(){
  $("#hide").css('display','none');
  $(".mobile-nav").fadeOut(50);
  $("#show").show();
  cdg.style.height = (window.innerHeight - 200) + "px";
  cdg.style.width = (window.innerWidth - 100) + "px";

}

function changeHeader(headers,json){
  
  for(var i=0;i<headers.length;i++){
   
    cdg.schema[i].title = headers[i];   
  }
  


}
/* make the buttons for the sheets */
var make_buttons = function(sheetnames, cb) {
  var buttons = document.getElementById('buttons');
  buttons.innerHTML = "";
  sheetnames.forEach(function(s,idx) {
    var btn = document.createElement('button');
    btn.style.height='35px';
    btn.style.padding='5px 5px 5px 5px';
    btn.type = 'button';
    btn.name = 'btn' + idx;
    btn.text = s;
    var txt = document.createElement('h5');
    txt.innerText = s;
    btn.appendChild(txt);
    btn.addEventListener('click', function() {cb(idx); hideView();}, false);
    buttons.appendChild(btn);
  });
  buttons.appendChild(document.createElement('br'));
};

var cdg = canvasDatagrid({
  parentNode: _grid
});
cdg.style.height = '100%';
cdg.style.width = '100%';
var colNum=0;
var rowNum=0;
var isVirtual=0;
cdg.addEventListener('click', function (e) {
  colNum=e.cell.columnIndex;
  rowNum=e.cell.rowIndex;

  var colNum_str=colNum.toString();
  var rowNum_str=rowNum.toString();
  if (!e.cell) { return; }
  
  if(e.cell.value==null){
    cdg.data[rowNum][colNum]=" ";
    cdg.draw();
    storeThisEdit(rowNum_str,colNum_str,cdg.data[rowNum][colNum]);
  }
  storeThisEdit(rowNum_str,colNum_str,cdg.data[rowNum][colNum]);
  var colval=cdg.schema[e.cell.columnIndex].title;
  colval=colval.charAt(0).toLowerCase() + colval.slice(1);
  var rowval=cdg.data[e.cell.rowIndex][0];

  if(colval=="Attribute"||colval=="Role"||colval=="Unit"||colval=="attribute"){
    isVirtual=0;
  }
  else if(colval=="attributeOf"||colval=="Time"||colval=="inRelationTo"||colval=="wasDerivedFrom"||colval=="wasGeneratedBy"
  || colval=="Relation"||colval=="Entity"){
    isVirtual=1;
  }
  closeMenu(isVirtual);
  clearMenu(isVirtual);

  if(colNum==0){
    hideView();
  }

  else{
    var menuoptns=[];
    if(demo_enabled){
      jsonparser(colval,rowval,menuoptns,isVirtual);
    }
    else{

      // Check if we have gotten recomendations yet
      if (typeof sdd_suggestions == 'undefined') {
         // Get suggestions
         getSuggestion();
         alert('Requesting Suggestions');
      }
      else{
         applySuggestion(colval,rowval,menuoptns,isVirtual);
      }
    }
  }
});

cdg.addEventListener('endedit',function(e){
  if (!e.cell) { return; }
  
  
  var colval=cdg.schema[e.cell.columnIndex].title;
  colval=colval.charAt(0).toLowerCase() + colval.slice(1);
  var rowval=cdg.data[e.cell.rowIndex][0];

  if(colval=="Attribute"||colval=="Role"||colval=="Unit"||colval=="attribute"){
    isVirtual=0;
  }
  else if(colval=="attributeOf"||colval=="Time"||colval=="inRelationTo"||colval=="wasDerivedFrom"||colval=="wasGeneratedBy"
  || colval=="Relation"||colval=="Entity"){
    isVirtual=1;
  }
  colNum=e.cell.columnIndex;
  rowNum=e.cell.rowIndex;
  var colNum_str=colNum.toString();
  var rowNum_str=rowNum.toString();
  storeThisEdit(rowNum_str,colNum_str,e.value);
  var menuoptns=[];
  starRec(colval,rowval,menuoptns,isVirtual,copyOfL,copyOfR,rowNum,colNum);
})

cdg.addEventListener('click', function (e) {

  if (!e.cell) { return; }
  if(e.cell.value==null){return;}
  else{
    colNum=e.cell.columnIndex;
    rowNum=e.cell.rowIndex;
    var varnameElement=cdg.data[rowNum][0];

    DDExceltoJSON(dd_url,varnameElement);
  }

});


function applySuggestion(colval, rowval, menuoptns, isVirtual) {
   var keyword="columns";
   if(rowval.startsWith("??")){
      keyword="virtual-columns";
   }

   for (const sddRow of sdd_suggestions["sdd"]["Dictionary Mapping"][keyword]){
      if(sddRow["column"] == rowval){
         for (const sddCol of sddRow[colval]){
            menuoptns.push([sddCol.star, sddCol.value]);
         }
         break; // After we find the correct value we can quit searching
      }
   }

   menuoptns=menuoptns.sort(sortByStar);
   createNewMenu(menuoptns,colval,isVirtual);
}



function chooseItem(data) {
  var choice=data.value.split(",");
  cdg.data[rowNum][colNum] = choice[1];
  var colNum_str=colNum.toString();
  var rowNum_str=rowNum.toString();
  storeThisEdit(rowNum_str,colNum_str,cdg.data[rowNum][colNum]);
  drawStars(rowNum,colNum);
  cdg.draw();
  
}

function insertRowAbove(){
  var intendedRow=parseFloat(rowNum);
  cdg.insertRow([],intendedRow); // The first argument splices a js array into the csv data, so to insert a blank row insert an empty array
}

function insertRowBelow(){
  var intendedRow=parseFloat(rowNum);
  cdg.insertRow([],intendedRow+1); // The first argument splices a js array into the csv data, so to insert a blank row insert an empty array
}

var storeRow=[];
function removeRow(){
  // alert("Warning! You are about to delete a row.");
  var temp=[];
  temp.push(rowNum);
  for(var i=0;i<cdg.data[rowNum].length+1;i++){
    if(cdg.data[rowNum][i]==null){
      temp.push(" "); 
    }
    else{
      temp.push(cdg.data[rowNum][i]);
    }
  } 
  for( var i=1;i<temp.length;i++){
    $.ajax({
      type : 'GET',
      url : 'http://localhost:9000/hadatac/annotator/sddeditor_v2/removingRow',
      data : {
        removedValue:temp[i]
      },
      success : function(data) {
        
      }
    });
  }
  
  
  storeRow.push(temp);
  var intendedRow=parseFloat(rowNum);
  cdg.deleteRow(intendedRow);


}
function _resize() {
  _grid.style.height = (window.innerHeight - 200) + "px";
  _grid.style.width = (window.innerWidth - 100) + "px";
}
_resize();

window.addEventListener('resize', _resize);
var click_ctr=0;
var _onsheet = function(json, sheetnames, select_sheet_cb) {

  document.getElementById('footnote').style.display = "none";
  click_ctr++;
  // console.log(click_ctr);
  make_buttons(sheetnames, select_sheet_cb);

  /* show grid */
  _grid.style.display = "block";
  _resize();

  /* set up table headers */
  var L = 0;
  var R=0;
  json.forEach(function(r) { if(L < r.length) L = r.length; });
  // console.log(L);
  //alert(json[0][0]);
  var headers=[];
  for(var i = 0; i <L; ++i) {
    headers.push(json[0][i]);
  }
  
  for(var i = json[0].length; i < L; ++i) {
    json[0][i] = "";
  }
  cdg.data = json;


  changeHeader(headers,json);
  for(var i=0;i<cdg.data.length;i++){
    if(cdg.data[i][0]!=null){
      R++;
    }
  }
  checkRecs(L,R,1);
  cdg.draw();
};


function parseJson_(keyword,rowval,colval,data,menuoptns,isVirtual){
    var virtualarray=Object.keys(data["sdd"]["Dictionary Mapping"][keyword]);
      var index=0;
      var checkcolval="";
      for (var i =0;i<data["sdd"]["Dictionary Mapping"][keyword].length;i++){
        if(data["sdd"]["Dictionary Mapping"][keyword][i]["column"]==rowval){
          index=i;
        }
      }
      var tempcolarray=Object.keys(data["sdd"]["Dictionary Mapping"][keyword][index]);
      for (var m=0;m<tempcolarray.length;m++){
          if(tempcolarray[m]==colval){
            checkcolval=tempcolarray[m];
          }
        }

      if(data["sdd"]["Dictionary Mapping"][keyword][index]["column"]==rowval && colval==checkcolval){
          for(var n=0;n<data["sdd"]["Dictionary Mapping"][keyword][index][colval].length;n++){
            var temp=[];
            temp.push(data["sdd"]["Dictionary Mapping"][keyword][index][colval][n].star);
            temp.push(data["sdd"]["Dictionary Mapping"][keyword][index][colval][n].value);
            menuoptns.push(temp);
          }

      }

     menuoptns=menuoptns.sort(sortByStar);
     createNewMenu(menuoptns,colval,isVirtual);
}

function getSuggestion(){
   var getJSON = function(url, callback) {

      // Generate data dictionary map be reading DD in
      var dataDictionary = []
      var oReq = new XMLHttpRequest();
      oReq.open("GET", dd_url, true);
      oReq.responseType = "arraybuffer";

      oReq.onload = function(e) {
         var arraybuffer = oReq.response;

         /* convert data to binary string */
         var data = new Uint8Array(arraybuffer);
         var arr = new Array();
         for (var i = 0; i != data.length; ++i) arr[i] = String.fromCharCode(data[i]);
         var bstr = arr.join("");

         /* Call XLSX */
         var workbook = XLSX.read(bstr, {
             type: "binary"
         });

         /* Get worksheet */
         var worksheet = workbook.Sheets[workbook.SheetNames[2]];
         var xlarray=XLSX.utils.sheet_to_json(worksheet, {
             raw: true
         });

         // Generate data dictionary
         for(var i=0; i<xlarray.length; i++){
            dataDictionary.push(
               {
                  "column": xlarray[i]['VARNAME '],
                  "description": xlarray[i]['VARDESC ']
               }
            );
         }

         // Generating Suggestion Request
         var request = {}
         request["source-urls"] = ["http://semanticscience.org/resource/"];
         request["N"] = 4;
         request["data-dictionary"] = dataDictionary
         console.log(request);

         var xhr = new XMLHttpRequest();
         xhr.open("POST", url);
         xhr.setRequestHeader("Content-Type", "application/json")
         xhr.setRequestHeader("cache-control", "no-cache");
         xhr.responseType = 'json';
         xhr.onload = function() {
             var status = xhr.status;
             if (status == 200) {
                 callback(null, xhr.response);
             } else {
                 callback(status);
             }
         };
         xhr.send(JSON.stringify(request));

      }
      oReq.send();
   };

   getJSON("http://127.0.0.1:5000/populate-sdd",  function(err, data) {
      if (err != null) {
         console.error(err);
      }
      else {
         sdd_suggestions = data
      }
   });
}

function jsonparser(colval,rowval,menuoptns,isVirtual){
  var getJSON = function(url, callback) {
  var xhr = new XMLHttpRequest();
  xhr.open('GET', url, true);
  xhr.responseType = 'json';
  xhr.onload = function() {
      var status = xhr.status;
      if (status == 200) {
          callback(null, xhr.response);
      } else {
          callback(status);
      }
  };

  xhr.send();
  };

  getJSON('http://128.113.106.57:5000/get-sdd/',  function(err, data) {
  if (err != null) {
      console.error(err);
  }

  else {
    if(rowval.startsWith("??")){
      var keyword="virtual-columns";
      parseJson_(keyword,rowval,colval,data,menuoptns,isVirtual);
  }
    else{
      var keyword="columns";
      parseJson_(keyword,rowval,colval,data,menuoptns,isVirtual);
      }
  }
  });
}

function addOptionsToMenu(menuoptns,select){
  for(var i=0;i<menuoptns.length;i++){
    if(menuoptns[i]!=','){
      var opt=menuoptns[i];
      var optns=document.createElement("option")
      optns.textContent=opt;
      optns.value=opt;
      select.appendChild(optns);
    }
}
}
function createNewMenu(menuoptns,colval,isVirtual){
  if(isVirtual==0){
    var select=document.getElementById("menulist"),menuoptns;
    addOptionsToMenu(menuoptns,select);
    displayMenu(menuoptns.length,isVirtual);
  }
  else if(isVirtual==1){
    var select=document.getElementById("virtuallist"),menuoptns;
    addOptionsToMenu(menuoptns,select);
    displayMenu(menuoptns.length,isVirtual);
  }
  }



function displayMenu(sizeOfMenu,isVirtual){
  if(sizeOfMenu>0 && isVirtual==0){
    var menu = document.getElementById("menulist");
    menu.style.display = "block";
    closeMenu(1);
  }
  else if (sizeOfMenu==0 && isVirtual==0){
    closeMenu(isVirtual);
  }
  else if(sizeOfMenu>0 && isVirtual==1){
      var menu = document.getElementById("virtuallist");
      menu.style.display = "block";
      closeMenu(0);
    }
  else if (sizeOfMenu==0 && isVirtual==1){
    closeMenu(isVirtual);
  }

}

function closeMenu(isVirtual){
  if(isVirtual==0){
    var menu = document.getElementById("menulist");
    menu.style.display = "none";
  }
  if(isVirtual==1){
    var menu = document.getElementById("virtuallist");
    menu.style.display = "none";
  }
}




function clearMenu(isVirtual){
  if(isVirtual==0){
    var selectbox=document.getElementById("menulist");
    if(selectbox.options==null){
    }
    else{
      for(var i = selectbox.options.length - 1 ; i > 0 ; i--){
        selectbox.remove(i);
    }

    }
  }
  else if(isVirtual==1){
    var selectbox=document.getElementById("virtuallist");
    if(selectbox.options==null){
    }
    else{
      for(var i = selectbox.options.length - 1 ; i > 0 ; i--){
        selectbox.remove(i);
    }

    }
  }
}


  function sortByStar(a,b){
    if (a[0] === b[0]) {
      return 0;
    }
    else {
        return (a[0] > b[0]) ? -1 : 1;
    }
  }


function clearTextbox(){
  document.getElementById("varDescription").value="";
}
function DDExceltoJSON(dd_url,varnameElement){


   var oReq = new XMLHttpRequest();
   oReq.open("GET", dd_url, true);
   oReq.responseType = "arraybuffer";

  oReq.onload = function(e) {
      var arraybuffer = oReq.response;

      /* convert data to binary string */
      var data = new Uint8Array(arraybuffer);
      var arr = new Array();
      for (var i = 0; i != data.length; ++i) arr[i] = String.fromCharCode(data[i]);
      var bstr = arr.join("");

      /* Call XLSX */
      var workbook = XLSX.read(bstr, {
          type: "binary"
      });


      var first_sheet_name = workbook.SheetNames[2];
      /* Get worksheet */
      var worksheet = workbook.Sheets[first_sheet_name];
      var xlarray=XLSX.utils.sheet_to_json(worksheet, {
          raw: true
      });
      var indx=0;
      clearTextbox();
      for(var i=0;i<xlarray.length;i++){
        if(xlarray[i]['VARNAME ']==varnameElement){
          indx=i;
          document.getElementById("varDescription").value=xlarray[indx]['VARDESC '];

        }

      }
  }

  oReq.send();


}


var closebtns = document.getElementsByClassName("remove");
  var i;

  for (i = 0; i < closebtns.length; i++) {
    closebtns[i].addEventListener("click", function() {
      this.parentElement.style.display = 'none';
    });
}
