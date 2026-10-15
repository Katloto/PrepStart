(() => {
  const search = document.getElementById('userSearch');
  const rows = document.querySelectorAll('#userTable tbody tr');
  if (search && rows.length) {
    search.addEventListener('input', () => {
      const term = search.value.toLocaleLowerCase().trim();
      rows.forEach(row => { row.hidden = !row.textContent.toLocaleLowerCase().includes(term); });
    });
  }

  const courseFilter = document.getElementById('courseFilter');
  const moduleSelect = document.getElementById('moduleId');
  if (courseFilter && moduleSelect) {
    courseFilter.addEventListener('change', () => {
      const selected = courseFilter.value;
      moduleSelect.value = '';
      Array.from(moduleSelect.options).forEach(option => {
        if (!option.value) return;
        option.hidden = !!selected && option.dataset.course !== selected;
        option.disabled = option.hidden;
      });
    });
  }
})();
