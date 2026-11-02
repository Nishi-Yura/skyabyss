// 狭い画面のメニューの開け閉めと、職の切り替え (戦いと職の頁。#job-<職> で開く職を決める)
(function () {
  var btn = document.querySelector(".nav-toggle");
  var nav = document.getElementById("site-nav");
  if (btn && nav) {
    btn.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      btn.setAttribute("aria-expanded", open ? "true" : "false");
    });
  }

  var tabs = Array.prototype.slice.call(document.querySelectorAll(".job-tabs [role=tab]"));
  if (!tabs.length) return;
  function show(job, scroll) {
    var found = false;
    tabs.forEach(function (t) {
      var on = t.getAttribute("data-job") === job;
      if (on) found = true;
      t.setAttribute("aria-selected", on ? "true" : "false");
      t.tabIndex = on ? 0 : -1;
      var panel = document.getElementById(t.getAttribute("aria-controls"));
      if (on) panel.removeAttribute("data-hide"); else panel.setAttribute("data-hide", "");
    });
    if (found && scroll) document.getElementById("jobs").scrollIntoView();
    return found;
  }
  tabs.forEach(function (t, i) {
    t.addEventListener("click", function () {
      show(t.getAttribute("data-job"));
      try { history.replaceState(null, "", "#job-" + t.getAttribute("data-job")); } catch (e) {}
    });
    t.addEventListener("keydown", function (e) {
      var d = e.key === "ArrowDown" || e.key === "ArrowRight" ? 1 : e.key === "ArrowUp" || e.key === "ArrowLeft" ? -1 : 0;
      if (!d) return;
      e.preventDefault();
      var next = tabs[(i + d + tabs.length) % tabs.length];
      next.focus();
      next.click();
    });
  });
  var m = /^#job-([a-z]+)$/.exec(location.hash);
  if (!(m && show(m[1], true))) show(tabs[0].getAttribute("data-job"));
})();
