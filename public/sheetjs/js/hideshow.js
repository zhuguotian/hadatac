//var element=document.getElementById

$(document).ready(
    function() {
        $("button.main-nav").click(function() {
            $("#show").css('display','none');
            $(".mobile-nav").fadeIn(50);
            $("#hide").show();
            cdg.style.height = (window.innerHeight - 200) + "px";
            cdg.style.width = (window.innerWidth - 400) + "px";
  
        });
        $("button.Close").click(function() {
            $("#hide").css('display','none');
            $(".mobile-nav").fadeOut(50);
            $("#show").show();
            cdg.style.height = (window.innerHeight - 200) + "px";
            cdg.style.width = (window.innerWidth - 100) + "px";
        });
    });


