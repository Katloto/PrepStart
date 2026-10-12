'use strict';
(() => {
  const search = document.getElementById('moduleSearch');
  const grid = document.getElementById('moduleGrid');
  const count = document.getElementById('searchCount');
  const empty = document.getElementById('noSearchResults');
  const cards = grid ? Array.from(grid.querySelectorAll('.module-link')) : [];
  if (search) {
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase();
      let visible = 0;
      cards.forEach(card => {
        const matches = `${card.dataset.name} ${card.dataset.code}`.toLocaleLowerCase().includes(query);
        card.hidden = !matches;
        if (matches) visible++;
      });
      if (count) count.textContent = query ? `${visible} matching` : '';
      if (empty) empty.hidden = visible !== 0;
    };
    search.addEventListener('input', filter);
  }
  document.getElementById('focusSearch')?.addEventListener('click', () => search?.focus());
  let ascending = true;
  document.getElementById('sortModules')?.addEventListener('click', (event) => {
    if (!grid) return;
    ascending = !ascending;
    cards.sort((a,b) => a.dataset.name.localeCompare(b.dataset.name) * (ascending ? 1 : -1));
    cards.forEach(card => grid.appendChild(card));
    event.currentTarget.querySelector('i')?.classList.toggle('fa-arrow-down-z-a', !ascending);
  });
  document.getElementById('toggleView')?.addEventListener('click', event => {
    if (!grid) return;
    const compact = grid.classList.toggle('list-view');
    event.currentTarget.innerHTML = compact ? 'List view <i class="fa-solid fa-list"></i>' : 'Card view <i class="fa-solid fa-chevron-down"></i>';
  });
  const tabs = [...document.querySelectorAll('[data-tab]')];
  tabs.forEach(tab => tab.addEventListener('click', () => {
    tabs.forEach(button => {
      const selected = button === tab;
      button.classList.toggle('selected-tab', selected);
      button.setAttribute('aria-selected', String(selected));
      const panel = document.getElementById('panel-' + button.dataset.tab);
      if (panel) panel.hidden = !selected;
    });
  }));
})();
