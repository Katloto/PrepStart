'use strict';
(() => {
  // MyModules interactions: no fake module data. All cards originate in Thymeleaf's Enrollment query.
  const grid = document.getElementById('moduleGrid');
  const search = document.getElementById('moduleSearch');
  const results = document.getElementById('searchCount');
  const noResults = document.getElementById('noSearchResults');
  const cards = grid ? Array.from(grid.querySelectorAll('.module-link')) : [];

  let ascending = true;
  const filter = () => {
    if (!grid || !search) return;
    const query = search.value.trim().toLocaleLowerCase();
    let shown = 0;
    cards.forEach(card => {
      const matches = `${card.dataset.name || ''} ${card.dataset.code || ''} ${card.dataset.course || ''}`
        .toLocaleLowerCase().includes(query);
      card.hidden = !matches;
      if (matches) shown += 1;
    });
    if (results) results.textContent = `${shown} ${shown === 1 ? 'module' : 'modules'}`;
    if (noResults) noResults.hidden = shown !== 0;
  };
  search?.addEventListener('input', filter);

  document.getElementById('sortModules')?.addEventListener('click', e => {
    if (!grid) return;
    ascending = !ascending;
    cards.sort((a, b) => (a.dataset.name || '').localeCompare(b.dataset.name || '') * (ascending ? 1 : -1));
    cards.forEach(card => grid.appendChild(card));
    const label = e.currentTarget.querySelector('span');
    if (label) label.textContent = ascending ? 'Sort: A to Z' : 'Sort: Z to A';
  });

  document.getElementById('toggleView')?.addEventListener('click', e => {
    if (!grid) return;
    const list = grid.classList.toggle('list-view');
    e.currentTarget.setAttribute('aria-pressed', String(list));
    const label = e.currentTarget.querySelector('span');
    if (label) label.textContent = list ? 'List view' : 'Card view';
  });

  // ModuleDetails tabs: only one category visible at a time, including empty states.
  const tabs = Array.from(document.querySelectorAll('[role="tab"][data-tab]'));
  const activate = tab => {
    tabs.forEach(button => {
      const selected = button === tab;
      button.classList.toggle('selected-tab', selected);
      button.setAttribute('aria-selected', String(selected));
      button.tabIndex = selected ? 0 : -1;
      const panel = document.getElementById('panel-' + button.dataset.tab);
      if (panel) panel.hidden = !selected;
    });
  };
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activate(tab));
    tab.addEventListener('keydown', e => {
      let target;
      if (e.key === 'ArrowRight') target = tabs[(index + 1) % tabs.length];
      else if (e.key === 'ArrowLeft') target = tabs[(index + tabs.length - 1) % tabs.length];
      else if (e.key === 'Home') target = tabs[0];
      else if (e.key === 'End') target = tabs[tabs.length - 1];
      if (!target) return;
      e.preventDefault();
      target.focus();
      activate(target);
    });
  });
})();
