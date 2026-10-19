window.addEventListener('pageshow', () => {
  document.querySelectorAll('[data-seat-choice]').forEach(form => { delete form.dataset.confirmed; });
  document.querySelectorAll('form[data-submitting]').forEach(form => {
    delete form.dataset.submitting; form.removeAttribute('aria-busy');
    form.querySelectorAll('[data-submission-button]').forEach(button => { button.disabled = false; delete button.dataset.submissionButton; });
  });
});
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.nav-shortcut').forEach(link => {
    const path = new URL(link.href).pathname;
    const home = path === '/' && ['/student', '/admin'].includes(location.pathname);
    if (home || (path !== '/' && (path === location.pathname || location.pathname.startsWith(path + '/')))) link.setAttribute('aria-current', 'page');
  });
  document.querySelectorAll('[data-auto-show="true"]').forEach(dialog => bootstrap.Modal.getOrCreateInstance(dialog).show());
  document.querySelectorAll('.modal').forEach(dialog => {
    dialog.addEventListener('shown.bs.modal', () => dialog.querySelector('input:not([type="hidden"]), textarea')?.focus());
  });
  const error = document.querySelector('.alert-danger[role="alert"]');
  if (error && !error.closest('.modal')) { error.tabIndex = -1; error.focus(); }
  document.querySelectorAll('.table-responsive').forEach(table => {
    table.tabIndex = 0;
    table.setAttribute('role', 'region');
    table.setAttribute('aria-label', '数据表格，可横向滚动查看');
  });
  let chosenSeat = null;
  const seatSubmit = document.querySelector('[data-seat-submit]');
  seatSubmit?.addEventListener('click', () => {
    if (!chosenSeat || chosenSeat.dataset.submitting) return;
    chosenSeat.dataset.confirmed = 'true';
    chosenSeat.requestSubmit(chosenSeat.querySelector('button[type="submit"]'));
  });
  document.querySelectorAll('form').forEach(form => {
    form.addEventListener('submit', event => {
      if (form.hasAttribute('data-seat-choice') && form.dataset.confirmed !== 'true' && seatSubmit) {
        event.preventDefault();
        chosenSeat = form;
        document.querySelectorAll('.seat-tile').forEach(tile => tile.classList.remove('is-selected'));
        document.querySelectorAll('[data-seat-choice] button').forEach(button => button.setAttribute('aria-pressed', 'false'));
        form.closest('.seat-tile').classList.add('is-selected');
        form.querySelector('button').setAttribute('aria-pressed', 'true');
        document.querySelector('[data-selected-seat]').textContent = form.dataset.seatName;
        document.querySelector('[data-seat-selection-status]').textContent = '已选择 ' + form.dataset.seatName + '，请核对日期和时段后确认。';
        seatSubmit.disabled = false;
        seatSubmit.focus({preventScroll: true});
        if (matchMedia('(max-width: 767px)').matches) seatSubmit.scrollIntoView({block: 'center', behavior: 'auto'});
        return;
      }
      if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) { event.preventDefault(); return; }
      if (form.dataset.submitting) { event.preventDefault(); return; }
      form.dataset.submitting = 'true'; form.setAttribute('aria-busy', 'true');
      if (event.submitter) { event.submitter.dataset.submissionButton = 'true'; event.submitter.disabled = true; }
    });
  });
});
