/* Vanilla JS port of the Navbar.jsx useState/useEffect interactions. */
(() => {
  const nav = document.getElementById('ps-navbar');
  if (!nav) return;
  const searchBox = nav.querySelector('[data-search-box]');
  const searchInput = nav.querySelector('[data-search-input]');
  const searchToggle = nav.querySelector('[data-search-toggle]');
  const profileMenu = nav.querySelector('[data-profile-menu]');
  const profileToggle = nav.querySelector('[data-profile-toggle]');
  const profileDropdown = nav.querySelector('[data-profile-dropdown]');
  if (searchToggle && searchBox && searchInput) {
    searchToggle.addEventListener('click', () => {
      const open = searchBox.classList.toggle('expanded');
      searchToggle.setAttribute('aria-expanded', String(open));
      if (open) searchInput.focus();
    });
    searchInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        const search = searchInput.value.trim();
        if (search) window.location.assign('/search?query=' + encodeURIComponent(search));
      }
    });
  }
  if (profileMenu && profileToggle && profileDropdown) {
    const close = () => {
      profileDropdown.hidden = true;
      profileToggle.setAttribute('aria-expanded', 'false');
    };
    profileToggle.addEventListener('click', () => {
      const nextOpen = profileDropdown.hidden;
      profileDropdown.hidden = !nextOpen;
      profileToggle.setAttribute('aria-expanded', String(nextOpen));
    });
    document.addEventListener('click', (event) => {
      if (!profileMenu.contains(event.target)) close();
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') { close(); profileToggle.focus(); }
    });
  }
})();
