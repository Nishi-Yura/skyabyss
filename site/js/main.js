/* 日本語 / English の切り替えと、狭い画面のメニュー。どちらも無くてもページは読める。 */
(function () {
  var KEY = "skyabyss-site-lang";
  var root = document.documentElement;

  function get() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function put(v) {
    try { localStorage.setItem(KEY, v); } catch (e) { /* 保存できなくても動く */ }
  }

  function apply(lang) {
    if (lang !== "en") lang = "ja";
    root.setAttribute("data-lang", lang);
    root.setAttribute("lang", lang);
    var t = document.querySelector("title");
    if (t && t.getAttribute("data-" + lang)) document.title = t.getAttribute("data-" + lang);
    var imgs = document.querySelectorAll("img[data-alt-en]");
    for (var i = 0; i < imgs.length; i++) {
      var im = imgs[i];
      if (!im.hasAttribute("data-alt-ja")) im.setAttribute("data-alt-ja", im.getAttribute("alt") || "");
      im.setAttribute("alt", im.getAttribute("data-alt-" + lang));
    }
    var svgs = document.querySelectorAll("[data-label-en]");
    for (var j = 0; j < svgs.length; j++) {
      var s = svgs[j];
      if (!s.hasAttribute("data-label-ja")) s.setAttribute("data-label-ja", s.getAttribute("aria-label") || "");
      s.setAttribute("aria-label", s.getAttribute("data-label-" + lang));
    }
    var btns = document.querySelectorAll(".lang button");
    for (var k = 0; k < btns.length; k++) {
      btns[k].setAttribute("aria-pressed", btns[k].getAttribute("data-set") === lang ? "true" : "false");
    }
  }

  root.className += " js";
  apply(get() === "en" ? "en" : "ja");

  document.addEventListener("DOMContentLoaded", function () {
    apply(root.getAttribute("data-lang"));
    var btns = document.querySelectorAll(".lang button");
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener("click", function () {
        var l = this.getAttribute("data-set");
        put(l);
        apply(l);
      });
    }
    var tog = document.querySelector(".nav-toggle");
    var nav = document.getElementById("site-nav");
    if (tog && nav) {
      tog.addEventListener("click", function () {
        var open = nav.classList.toggle("open");
        tog.setAttribute("aria-expanded", open ? "true" : "false");
      });
    }
  });
})();
