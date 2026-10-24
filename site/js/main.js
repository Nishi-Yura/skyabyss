/* 日本語 / English の切り替え、表示（自動・明るい・暗い）、狭い画面のメニュー。どれも無くてもページは読める。 */
(function () {
  var LANG_KEY = "skyabyss-site-lang";
  var THEME_KEY = "skyabyss-site-theme";
  var root = document.documentElement;

  function store(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }
  function save(key, v) {
    try { localStorage.setItem(key, v); } catch (e) { /* 保存できなくても動く */ }
  }

  /* ---- 言語 ---- */
  function applyLang(lang) {
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

  /* ---- 表示（テーマ） ---- */
  var THEME_LABEL = {
    auto: ["表示: 自動", "Theme: auto"],
    light: ["表示: 明るい", "Theme: light"],
    dark: ["表示: 暗い", "Theme: dark"]
  };
  function currentTheme() {
    var t = root.getAttribute("data-theme");
    return t === "light" || t === "dark" ? t : "auto";
  }
  function applyTheme(mode) {
    if (mode === "light" || mode === "dark") root.setAttribute("data-theme", mode);
    else root.removeAttribute("data-theme");
    var btn = document.querySelector("[data-theme-toggle]");
    if (btn) {
      var lab = THEME_LABEL[mode] || THEME_LABEL.auto;
      btn.innerHTML = '<span class="ja" lang="ja">' + lab[0] + '</span><span class="en" lang="en">' + lab[1] + "</span>";
      btn.setAttribute("data-mode", mode);
      btn.setAttribute("aria-label", lab[0] + " / " + lab[1]);
    }
  }

  applyLang(store(LANG_KEY) === "en" ? "en" : "ja");
  root.className += " js";

  document.addEventListener("DOMContentLoaded", function () {
    applyLang(root.getAttribute("data-lang") || "ja");
    applyTheme(currentTheme());

    var langBtns = document.querySelectorAll(".lang button");
    for (var i = 0; i < langBtns.length; i++) {
      langBtns[i].addEventListener("click", function () {
        var l = this.getAttribute("data-set");
        save(LANG_KEY, l);
        applyLang(l);
      });
    }

    var themeBtn = document.querySelector("[data-theme-toggle]");
    if (themeBtn) {
      themeBtn.addEventListener("click", function () {
        var order = ["auto", "light", "dark"];
        var next = order[(order.indexOf(currentTheme()) + 1) % order.length];
        var mode = next === "auto" ? "" : next;
        save(THEME_KEY, mode);
        applyTheme(next);
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

    // 技の動く絵: パネルを開いたときだけ読み、閉じたら外す (それまでは止めた絵)。
    // 動きを減らす設定の人は自動では読まず、再生ボタンを押したときに読む
    var calm = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
    var fxClips = document.querySelectorAll(".fx-clip");
    var loadClip = function (fig) {
      if (fig.getAttribute("data-live")) return;
      var slot = fig.querySelector(".fx-slot");
      var btn = fig.querySelector(".fx-play");
      var im = new Image();
      im.width = 640;
      im.height = 360;
      im.alt = "";
      im.onload = function () {
        slot.appendChild(im);
        fig.setAttribute("data-live", "1");
        if (btn) btn.hidden = true;
      };
      im.src = fig.getAttribute("data-src");
    };
    var unloadClip = function (fig) {
      var slot = fig.querySelector(".fx-slot");
      if (slot) slot.innerHTML = "";
      fig.removeAttribute("data-live");
      var btn = fig.querySelector(".fx-play");
      if (btn && calm) btn.hidden = false;
    };
    for (var c = 0; c < fxClips.length; c++) {
      (function (fig) {
        var btn = fig.querySelector(".fx-play");
        var det = fig.closest ? fig.closest("details") : null;
        if (btn) {
          btn.addEventListener("click", function () { loadClip(fig); });
          if (calm) btn.hidden = false;
        }
        if (det) {
          det.addEventListener("toggle", function () {
            if (det.open) { if (!calm) loadClip(fig); } else { unloadClip(fig); }
          });
        }
      })(fxClips[c]);
    }
  });
})();
