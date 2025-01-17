package org.hadatac.console.controllers.fileviewer;

import java.util.List;
import java.util.ArrayList;

import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.models.SysUser;
import org.hadatac.console.views.html.fileviewer.*;
import org.hadatac.entity.pojo.DataFile;
import org.hadatac.utils.ConfigProp;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import play.mvc.Controller;
import play.mvc.Result;
import org.hadatac.utils.NameSpaces;
import play.libs.Json;



public class SDDEditorV2 extends Controller {
    NameSpaces ns = NameSpaces.getInstance();
        List<String> loadedList=ns.listLoadedOntologies();
        List<String> currentCart=new ArrayList<String>();
        ArrayList<ArrayList<String>> storeEdits=new ArrayList<ArrayList<String>>();
        ArrayList<ArrayList<String>> oldEdits=new ArrayList<ArrayList<String>>();
       // ArrayList<ArrayList<String>> storeRows=new ArrayList<ArrayList<String>>();
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))


    public Result index(String fileId, boolean bSavable) {

        final SysUser user = AuthApplication.getLocalUser(session());
        DataFile dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        if (null == dataFile) {

            return ok(sdd_editor_v2.render(dataFile, null, false,loadedList,this));
        }


        List<DataFile> files = null;
        String path = ConfigProp.getPathDownload();

        files = DataFile.find(user.getEmail());

        String dd_filename=dataFile.getFileName();
        dd_filename = dd_filename.substring(1); // Only files with the prefix SDD are allowed so were always going to have a second character
        DataFile dd_dataFile = new DataFile(""); // This is being used in place of null but we might want to come up with a better way

        for(DataFile df : files){
           if(df.getFileName().equals(dd_filename)){
             dd_dataFile = df;
          }
       }


    	// System.out.println("files = " + files);
    	// System.out.println("dd_dataFile = " + dd_dataFile.getFileName());


        return ok(sdd_editor_v2.render(dataFile, dd_dataFile, bSavable,loadedList,this));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result postIndex(String fileId, boolean bSavable) {
        return index(fileId, bSavable);
    }

    public Result fromSharedLink(String sharedId) {
        DataFile dataFile = DataFile.findBySharedId(sharedId);
        if (null == dataFile) {
            return badRequest("Invalid link!");
        }

        return ok(sdd_editor_v2.render(dataFile,null, false,loadedList,this));
    }

    public Result postFromSharedLink(String sharedId) {
        return fromSharedLink(sharedId);
    }

    // public Result testPrint(String s){
    //     System.out.println(s);
    //     return new Result(200);
    // }

    public Result getCart(){
        return ok(Json.toJson(currentCart));
    }

    public Result addToCart(String ontology){
        if(currentCart.contains(ontology)){
            System.out.println("This item already exists");
        }
        else{
            currentCart.add(ontology);
        }
        
        return new Result(200);
    }

    public Result removeFromCart(String item){
       
       currentCart.remove(item);
       
        return ok(Json.toJson(currentCart));
    }
    public Result sizeOfCart(int cartamount){
        cartamount= currentCart.size();
        System.out.println(cartamount);
        return ok(Json.toJson(cartamount));
        
    }

    public Result addToEdits(String row, String col,String editValue){
        ArrayList<String> temp = new ArrayList<String>();
        temp.add(row);
        temp.add(col);
        temp.add(editValue);
        storeEdits.add(temp);
        //return new Result(200);
        return ok(Json.toJson(storeEdits));
    }
   
    public Result removingRow(String removedValue){
        for( int i=0;i<storeEdits.size();i++){
            if(storeEdits.get(i).get(2)==removedValue){
                storeEdits.remove(storeEdits.get(i));
            }
        }
        return ok(Json.toJson(storeEdits));
    }

    public Result getEdit(){
        ArrayList<String> temp=storeEdits.get(storeEdits.size()-1);
        
        //String lastKnown=;
        System.out.println(temp);
        oldEdits.add(temp);
        storeEdits.remove(storeEdits.size()-1);
        
        ArrayList<String> lastEdit=storeEdits.get(storeEdits.size()-1);
        return ok(Json.toJson(lastEdit));
    }
    
    public Result getOldEdits(){
         ArrayList<String> recentoldEdit=oldEdits.get(0);
         oldEdits.remove(0);
        return ok(Json.toJson(recentoldEdit));
    }


    
}
