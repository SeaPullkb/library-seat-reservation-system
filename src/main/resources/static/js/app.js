document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-auto-show="true"]').forEach(dialog => {
    bootstrap.Modal.getOrCreateInstance(dialog).show();
  });
  document.querySelectorAll('.modal').forEach(dialog => {
    dialog.addEventListener('shown.bs.modal', () => {
      dialog.querySelector('input:not([type="hidden"]), textarea')?.focus();
    });
  });
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', event => {
      if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    });
  });
});
