window.addEventListener('pageshow', () => {
  document.querySelectorAll('form[data-submitting]').forEach(form => {
    delete form.dataset.submitting; form.removeAttribute('aria-busy');
    form.querySelectorAll('[data-submission-button]').forEach(button => { button.disabled = false; delete button.dataset.submissionButton; });
  });
});
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.nav-shortcut').forEach(link => {
    if (new URL(link.href).pathname === location.pathname) link.setAttribute('aria-current', 'page');
  });
  document.querySelectorAll('[data-auto-show="true"]').forEach(dialog => bootstrap.Modal.getOrCreateInstance(dialog).show());
  document.querySelectorAll('.modal').forEach(dialog => {
    dialog.addEventListener('shown.bs.modal', () => dialog.querySelector('input:not([type="hidden"]), textarea')?.focus());
  });
  const error = document.querySelector('.alert-danger[role="alert"]');
  if (error && !error.closest('.modal')) { error.tabIndex = -1; error.focus(); }
  document.querySelectorAll('form').forEach(form => {
    form.addEventListener('submit', event => {
      if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) { event.preventDefault(); return; }
      if (form.dataset.submitting) { event.preventDefault(); return; }
      form.dataset.submitting = 'true'; form.setAttribute('aria-busy', 'true');
      if (event.submitter) { event.submitter.dataset.submissionButton = 'true'; event.submitter.disabled = true; }
    });
  });
});
