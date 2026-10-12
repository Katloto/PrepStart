/** PrepStart upload page: module picker, topic suggestions and real file upload UX. */
(() => {
  'use strict';
  const form = document.getElementById('materialUploadForm');
  if (!form) return;
  const course = document.getElementById('courseId');
  const module = document.getElementById('moduleId');
  const year = document.getElementById('academicYear');
  const allModules = Array.from(document.querySelectorAll('#availableModules option'));
  const topicSuggestions = document.getElementById('topicSuggestions');
  const dropzone = document.getElementById('uploadDropzone');
  const fileInput = document.getElementById('fileInput');
  const selectedFile = document.getElementById('selectedFile');
  const selectedFileName = document.getElementById('selectedFileName');
  const selectedFileSize = document.getElementById('selectedFileSize');
  const submit = document.getElementById('uploadSubmit');
  const submitText = document.getElementById('uploadSubmitText');
  let topicFetchSequence = 0;

  function renderModules() {
    const selectedCourse = course.value;
    module.replaceChildren(new Option(selectedCourse ? 'Select your module' : 'Choose a course first', ''));
    const matched = allModules.filter(opt => opt.dataset.courseId === selectedCourse);
    for (const opt of matched) module.add(new Option(opt.textContent.trim(), opt.value));
    module.disabled = !selectedCourse || matched.length === 0;
    if (matched.length === 0 && selectedCourse) module.options[0].textContent = 'No modules available for this course';
    topicSuggestions.replaceChildren();
    topicFetchSequence += 1;
  }

  course.addEventListener('change', renderModules);
  module.addEventListener('change', async () => {
    const moduleId = module.value;
    topicSuggestions.replaceChildren();
    const request = ++topicFetchSequence;
    if (!moduleId) return;
    const selected = allModules.find(opt => opt.value === moduleId);
    if (selected && /^\d{4}$/.test(selected.dataset.year)) year.value = selected.dataset.year;
    try {
      const response = await fetch('/uploadcontent/topics?moduleId=' + encodeURIComponent(moduleId), {
        credentials: 'same-origin', headers: { 'Accept': 'application/json' }
      });
      if (!response.ok) return;
      const names = await response.json();
      if (request !== topicFetchSequence) return;
      names.forEach(name => topicSuggestions.appendChild(new Option(name)));
    } catch (_) { /* Topics are optional; the upload form remains usable. */ }
  });

  const allowed = new Set(['pdf','doc','docx','ppt','pptx','txt','xls','xlsx']);
  const maxBytes = 20 * 1024 * 1024;
  function showFile() {
    const file = fileInput.files && fileInput.files[0];
    if (!file) { selectedFile.hidden = true; dropzone.classList.remove('up-has-file'); return; }
    const ext = (file.name.split('.').pop() || '').toLowerCase();
    if (!allowed.has(ext) || file.size > maxBytes) {
      fileInput.value = '';
      selectedFile.hidden = true;
      dropzone.classList.remove('up-has-file');
      alert(!allowed.has(ext) ? 'Unsupported file type. Please choose PDF, Office, or TXT.' : 'File is larger than the 20 MB limit.');
      return;
    }
    selectedFileName.textContent = file.name;
    selectedFileSize.textContent = (file.size / (1024 * 1024)).toFixed(2) + ' MB';
    selectedFile.hidden = false;
    dropzone.classList.add('up-has-file');
  }
  fileInput.addEventListener('change', showFile);
  ['dragenter', 'dragover'].forEach(type => dropzone.addEventListener(type, event => {
    event.preventDefault(); dropzone.classList.add('up-dragging');
  }));
  ['dragleave','dragend'].forEach(type => dropzone.addEventListener(type, event => {
    event.preventDefault(); dropzone.classList.remove('up-dragging');
  }));
  dropzone.addEventListener('drop', event => {
    event.preventDefault(); dropzone.classList.remove('up-dragging');
    if (!event.dataTransfer || !event.dataTransfer.files.length) return;
    const transfer = new DataTransfer();
    transfer.items.add(event.dataTransfer.files[0]);
    fileInput.files = transfer.files;
    showFile();
  });
  form.addEventListener('reset', () => {
    setTimeout(() => { renderModules(); selectedFile.hidden = true; dropzone.classList.remove('up-has-file'); }, 0);
  });
  form.addEventListener('submit', event => {
    if (!form.checkValidity()) return;
    const file = fileInput.files && fileInput.files[0];
    if (!file || file.size > maxBytes) { event.preventDefault(); showFile(); return; }
    submit.disabled = true;
    submitText.textContent = 'Uploading…';
    // The browser submits a real multipart POST; no fake upload progress.
  });
  renderModules();
})();
