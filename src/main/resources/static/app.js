'use strict';
const $ = (selector) => document.querySelector(selector);
const state = { user: null, csrf: null, categories: [], transactions: [], budgets: [], summary: null, view: 'overview', page: 0, registering: false, editing: null, generation: 0, loading: false, dataMonth: null };
const money = (value) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
const palette = ['#297e67', '#b98b63', '#7a8870', '#d2af76', '#6a777b', '#ad8271', '#a3b79b'];
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const localDate = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
const monthName = () => new Date(`${$('#month').value}-02T12:00:00`).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
const categoryName = (id) => state.categories.find((c) => c.id === id)?.name || 'Other';
let toastTimer;
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 4200); }
async function refreshCsrf() { const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' }); if (!response.ok) throw new Error('Could not establish a session. Please refresh.'); state.csrf = await response.json(); }
async function api(path, { method = 'GET', body, form = false } = {}) {
 const headers = {};
 if (method !== 'GET') { if (!state.csrf) await refreshCsrf(); headers[state.csrf.headerName] = state.csrf.token; }
 if (body !== undefined) headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
 const response = await fetch(path, { method, headers, credentials: 'same-origin', cache: 'no-store', body: body === undefined ? undefined : form ? new URLSearchParams(body) : JSON.stringify(body) });
 if (!response.ok) {
  const problem = await response.json().catch(() => ({}));
  if (response.status === 401 && state.user) { state.user = null; state.transactions = []; state.budgets = []; state.summary = null; state.generation++; showAuth(); }
  if (response.status === 403) state.csrf = null;
  throw new Error(problem.errors ? Object.entries(problem.errors).map(([field, message]) => `${field}: ${message}`).join('. ') : problem.detail || `Request failed (${response.status}). Please try again.`);
 }
 return response.status === 204 || response.headers.get('content-length') === '0' ? null : response.text().then((text) => text ? JSON.parse(text) : null);
}
function showAuth() { $('#app').hidden = true; $('#auth').hidden = false; $('#boot').hidden = true; if ($('#edit-dialog').open) $('#edit-dialog').close(); }
function authMode(register) {
 state.registering = register; $('#name-label').hidden = !register; $('#auth-name').required = register; $('#password-hint').hidden = !register;
 $('#auth-password').minLength = register ? 12 : 1; $('#auth-password').autocomplete = register ? 'new-password' : 'current-password';
 $('#auth-kicker').textContent = register ? 'A FRESH START' : 'WELCOME BACK'; $('#auth-title').innerHTML = register ? 'A little more clarity.<br>A little less worry.' : 'Your next chapter<br>starts here.';
 $('#auth-description').textContent = register ? 'Create your private space. Start with a clean slate.' : 'Sign in to see how your month is taking shape.';
 $('#auth-submit').textContent = register ? 'Create account ↗' : 'Sign in ↗'; $('#switch-copy').textContent = register ? 'Already have an account?' : 'New here?';
 $('#switch-auth').textContent = register ? 'Sign in' : 'Create an account'; $('#auth-error').textContent = '';
}
$('#switch-auth').addEventListener('click', () => authMode(!state.registering));
$('#show-password').addEventListener('click', () => { const show = $('#auth-password').type === 'password'; $('#auth-password').type = show ? 'text' : 'password'; $('#show-password').textContent = show ? 'Hide' : 'Show'; $('#show-password').setAttribute('aria-label', show ? 'Hide password' : 'Show password'); });
$('#auth-form').addEventListener('submit', async (event) => {
 event.preventDefault(); $('#auth-submit').disabled = true; $('#auth-error').textContent = '';
 try {
  const email = $('#auth-email').value.trim().toLowerCase(), password = $('#auth-password').value;
  await refreshCsrf();
  if (state.registering) await api('/api/auth/register', { method: 'POST', body: { name: $('#auth-name').value.trim(), email, password } });
  await api('/api/auth/login', { method: 'POST', form: true, body: { email, password } });
  $('#auth-password').value = ''; await refreshCsrf(); await enterApp();
 } catch (error) { $('#auth-error').textContent = error.message; } finally { $('#auth-submit').disabled = false; }
});
$('#logout').addEventListener('click', async () => {
 try { await api('/api/auth/logout', { method: 'POST' }); state.user = null; state.transactions = []; state.budgets = []; state.summary = null; state.generation++; state.csrf = null; showAuth(); authMode(false); await refreshCsrf(); }
 catch (error) { toast(error.message); }
});
async function enterApp() {
 state.user = await api('/api/auth/me'); state.categories = await api('/api/categories');
 $('#user-name').textContent = state.user.name; $('#user-avatar').textContent = state.user.name.charAt(0).toUpperCase();
 $('#category').innerHTML = state.categories.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
 $('#auth').hidden = true; $('#boot').hidden = true; $('#app').hidden = false; $('#month').value = localDate().slice(0,7); $('#search').value = ''; $('#type-filter').value = 'ALL';
 changeView('overview'); await loadMonth();
}
async function loadMonth() {
 const generation = ++state.generation; const month = $('#month').value;
 if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || month < '0001-01' || month > '9998-12') { toast('Choose a valid month.'); return; }
 state.loading = true; state.dataMonth = null;
 $('.stats').hidden = true;
 document.querySelectorAll('.view').forEach((view) => { view.hidden = true; });
 $('#sample-data').hidden = true;
 $('#add-transaction').disabled = true; $('#dock-add').disabled = true;
 $('#dashboard-loading').hidden = false; $('#load-error').hidden = true;
 try {
  const [summary, budgets] = await Promise.all([api(`/api/summaries?month=${month}`), api(`/api/budgets?month=${month}`)]);
  const transactions = []; let offset = 0;
  while (true) { const batch = await api(`/api/transactions?month=${month}&limit=100&offset=${offset}`); transactions.push(...batch); if (batch.length < 100) break; offset += 100; if (generation !== state.generation) return; }
  if (generation !== state.generation) return;
  state.summary = summary; state.transactions = transactions; state.budgets = budgets; state.page = 0;
  state.loading = false; state.dataMonth = month; render(); changeView(state.view);
 } catch (error) { if (generation === state.generation) { $('#load-error span').textContent = error.message; $('#load-error').hidden = false; } }
 finally { if (generation === state.generation) { state.loading = false; $('#dashboard-loading').hidden = true; $('#add-transaction').disabled = false; $('#dock-add').disabled = false; } }
}
$('#month').addEventListener('change', loadMonth); $('#retry').addEventListener('click', loadMonth);
function changeView(view) {
 state.view = view;
 const copy = { overview: ['YOUR MONTH, AT A GLANCE','Overview','Small steps. A healthier financial picture.'], transactions: ['THE EVERYDAY DETAILS','Transactions','Every entry is part of your story.'], budgets: ['A LITTLE INTENTION GOES A LONG WAY','Budgets','Make a plan. Leave room for life.'], reports: ['SEE THE BIGGER PICTURE','Reports','Understand your habits. Find your balance.'] }[view];
 if (!copy) return;
 $('#page-kicker').textContent = copy[0]; $('#page-title').textContent = copy[1]; $('#page-description').textContent = copy[2];
 ['overview','transactions','budgets','reports'].forEach((name) => { $(`#${name}-view`).hidden = name !== view || state.loading || state.dataMonth !== $('#month').value; });
 document.querySelectorAll('.dock [data-view]').forEach((button) => { button.classList.toggle('active', button.dataset.view === view); button.setAttribute('aria-current', button.dataset.view === view ? 'page' : 'false'); });
}
document.querySelectorAll('[data-view]').forEach((button) => button.addEventListener('click', () => changeView(button.dataset.view)));
function empty(title, detail) { return `<div class="empty-state"><span class="empty-symbol">◌</span><strong>${escapeHtml(title)}</strong>${escapeHtml(detail)}</div>`; }
function expenseGroups() {
 const grouped = new Map(); state.transactions.filter((t) => t.type === 'EXPENSE').forEach((t) => grouped.set(t.categoryId, (grouped.get(t.categoryId) || 0) + Number(t.amount)));
 return [...grouped].map(([id, amount]) => ({ id, name: categoryName(id), amount })).sort((a,b) => b.amount-a.amount);
}
function render() {
 $('.stats').hidden = false;
 const s = state.summary; $('#balance').textContent = money(s.balance); $('#income').textContent = money(s.income); $('#expenses').textContent = money(s.expenses);
 const rate = Number(s.income) > 0 ? Number(s.balance) / Number(s.income) * 100 : null;
 $('#savings-rate').textContent = rate === null ? '—' : `${rate.toFixed(1)}%`; $('#rate-fill').style.width = `${Math.max(0,Math.min(100,rate || 0))}%`;
 $('#savings-note').textContent = rate === null ? 'Add income to see your savings rate' : 'Balance as a share of income';
 $('#sample-data').hidden = state.transactions.length > 0 || state.budgets.length > 0;
 renderCashChart(); renderSpending(); renderTable(); renderBudgets(); renderReport();
 $('#recent-transactions').innerHTML = transactionTable(state.transactions.slice(0,4), false);
}
function renderCashChart() {
 const days = new Date(Number($('#month').value.slice(0,4)),Number($('#month').value.slice(5)),0).getDate();
 const income = Array(days).fill(0), expense = Array(days).fill(0);
 state.transactions.forEach((t) => (t.type === 'INCOME' ? income : expense)[Number(t.date.slice(8))-1] += Number(t.amount));
 const max = Math.max(...income,...expense,1), w = 560, h = 155, x = (i) => 40+i*(w-55)/(days-1), y = (v) => h-12-v/max*(h-22);
 const points = (values) => values.map((v,i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
 let grid = ''; for (let n=0;n<4;n++) { const v=max*n/3; grid += `<line class="chart-grid" x1="40" y1="${y(v)}" x2="${w}" y2="${y(v)}"/><text class="chart-label" x="0" y="${y(v)+3}">${v >= 1000 ? (v/1000).toFixed(1)+'k' : v.toFixed(0)}</text>`; }
 const labels = [0,Math.floor(days/3),Math.floor(days*2/3),days-1].map((d) => `<text class="chart-label" text-anchor="middle" x="${x(d)}" y="175">${d+1} ${new Date($('#month').value+'-02T12:00:00').toLocaleDateString('en-US',{month:'short'})}</text>`).join('');
 $('#cash-chart').innerHTML = `<svg viewBox="0 0 570 185" role="img" aria-label="Daily income and expenses for ${escapeHtml(monthName())}"><title>Daily income and expenses. Monthly income ${money(state.summary.income)}, expenses ${money(state.summary.expenses)}.</title>${grid}<polygon points="40,143 ${points(income)} ${x(days-1)},143" fill="#31866c0d"/><polyline points="${points(income)}" fill="none" stroke="#287e65" stroke-width="2.4" stroke-linejoin="round"/><polyline points="${points(expense)}" fill="none" stroke="#aa8362" stroke-width="2.4" stroke-linejoin="round"/>${labels}</svg>`;
}
function renderSpending() {
 const groups = expenseGroups(), total = groups.reduce((sum,g) => sum+g.amount,0); let offset = 0;
 const rings = groups.map((g,i) => { const length = g.amount/total*100; const ring = `<circle cx="60" cy="60" r="48" pathLength="100" fill="none" stroke="${palette[i%palette.length]}" stroke-width="10" stroke-dasharray="${Math.max(0,length-1)} ${100-Math.max(0,length-1)}" stroke-dashoffset="${-offset}"/>`; offset += length; return ring; }).join('');
 $('#spending-chart').innerHTML = `<div class="spend-layout"><div class="donut"><svg viewBox="0 0 120 120" role="img" aria-label="Spending by category"><circle cx="60" cy="60" r="48" fill="none" stroke="#d8d2c5" stroke-width="10"/>${rings}</svg><div class="donut-label"><small>Total spent</small><strong>${money(total)}</strong></div></div><div>${groups.length ? groups.map((g,i) => `<div class="category-row"><span class="category-name"><i class="category-dot" style="background:${palette[i%palette.length]}"></i>${escapeHtml(g.name)}</span><span>${(g.amount/total*100).toFixed(0)}%</span></div>`).join('') : '<p class="small muted">A fresh start.<br>Your spending breakdown will appear here.</p>'}</div></div>`;
}
function transactionTable(rows, actions) {
 if (!rows.length) return empty('Nothing here just yet.', 'Add a transaction to start your financial picture.');
 return `<div class="table-wrap"><table><thead><tr><th>Description</th><th>Category</th><th>Amount</th><th>Date</th>${actions ? '<th>Actions</th>' : ''}</tr></thead><tbody>${rows.map((t) => `<tr><td><div class="transaction-description"><span class="transaction-icon">${t.type === 'INCOME' ? '↙' : '↗'}</span><span class="description-text" title="${escapeHtml(t.description)}">${escapeHtml(t.description)}</span></div></td><td><span class="badge">${escapeHtml(categoryName(t.categoryId))}</span></td><td class="${t.type === 'INCOME' ? 'amount-in' : 'amount-out'}">${t.type === 'INCOME' ? '+' : '−'}${money(t.amount)}</td><td>${new Date(t.date+'T12:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric'})}</td>${actions ? `<td><div class="row-actions"><button data-edit-transaction="${t.id}" aria-label="Edit ${escapeHtml(t.description)}">Edit</button><button class="delete" data-delete-transaction="${t.id}" aria-label="Delete ${escapeHtml(t.description)}">Delete</button></div></td>` : ''}</tr>`).join('')}</tbody></table></div>`;
}
function filteredTransactions() { const search = $('#search').value.toLowerCase().trim(), type = $('#type-filter').value; return state.transactions.filter((t) => t.description.toLowerCase().includes(search) && (type === 'ALL' || type === t.type)); }
function renderTable() {
 const rows = filteredTransactions(), pages = Math.max(1,Math.ceil(rows.length/8)); state.page = Math.min(state.page,pages-1);
 $('#transaction-table').innerHTML = transactionTable(rows.slice(state.page*8,state.page*8+8),true); $('#transaction-count').textContent = `${rows.length} transaction${rows.length === 1 ? '' : 's'} · ${monthName()}`;
 $('#page-info').textContent = `${state.page+1} / ${pages}`; $('#previous-page').disabled = state.page === 0; $('#next-page').disabled = state.page >= pages-1;
}
$('#search').addEventListener('input', () => { state.page = 0; renderTable(); }); $('#type-filter').addEventListener('change', () => { state.page = 0; renderTable(); });
$('#previous-page').addEventListener('click', () => { state.page--; renderTable(); }); $('#next-page').addEventListener('click', () => { state.page++; renderTable(); });
function budgetProgress(b) { const progress = state.summary.budgets.find((p) => p.categoryId === b.categoryId); const spent = Number(progress?.spent || 0), remaining = Number(b.amount)-spent; return `<div class="budget-item ${remaining < 0 ? 'over' : ''}"><div class="budget-top"><span>${escapeHtml(categoryName(b.categoryId))}</span><span>${money(b.amount)}</span></div><div class="progress-track" role="progressbar" aria-label="${escapeHtml(categoryName(b.categoryId))} budget used" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100,spent/Number(b.amount)*100).toFixed(0)}"><i style="width:${Math.min(100,spent/Number(b.amount)*100)}%"></i></div><div class="budget-bottom"><span>${money(spent)} spent</span><span>${money(Math.abs(remaining))} ${remaining < 0 ? 'over budget' : 'left'}</span></div></div>`; }
function renderBudgets() {
 $('#budget-preview').innerHTML = state.budgets.length ? state.budgets.slice(0,3).map(budgetProgress).join('') : empty('Give your money a plan.', 'Set a monthly budget for a category.');
 $('#budget-grid').innerHTML = state.budgets.length ? state.budgets.map((b) => `<article class="budget-card">${budgetProgress(b)}<div class="row-actions"><button data-edit-budget="${b.id}" aria-label="Edit ${escapeHtml(categoryName(b.categoryId))} budget">Edit budget</button><button class="delete" data-delete-budget="${b.id}" aria-label="Delete ${escapeHtml(categoryName(b.categoryId))} budget">Delete</button></div></article>`).join('') : empty('Start with one category.', 'A small plan makes a big difference.');
}
function renderReport() {
 $('#report-month').textContent = monthName(); const groups = expenseGroups(), s = state.summary;
 $('#report-content').innerHTML = `<div class="report-grid"><div><table><thead><tr><th>Category</th><th>Spent</th><th>Share</th></tr></thead><tbody>${groups.map((g) => `<tr><td>${escapeHtml(g.name)}</td><td>${money(g.amount)}</td><td>${(g.amount/Number(s.expenses)*100).toFixed(1)}%</td></tr>`).join('')}</tbody></table>${groups.length ? '' : empty('No expenses recorded.', 'Your category report will appear as you go.')}</div><div class="report-summary"><p class="eyebrow">THE BIG PICTURE</p><p>You recorded <strong>${state.transactions.length} transactions</strong> this month.</p><p>With <strong>${money(s.income)}</strong> coming in and <strong>${money(s.expenses)}</strong> going out, your monthly balance is <strong>${money(s.balance)}</strong>.</p><p>${groups.length ? `${escapeHtml(groups[0].name)} was your largest spending category.` : 'Add your first expense to start spotting patterns.'}</p></div></div>`;
}
function openEditor(kind, id = null) {
 const record = id ? (kind === 'transaction' ? state.transactions : state.budgets).find((item) => item.id === id) : null;
 state.editing = { kind, id, month: $('#month').value }; $('#edit-form').reset(); $('#edit-error').textContent = '';
 $('#dialog-title').textContent = `${id ? 'Edit' : 'Add'} ${kind}`; $('#save-record').textContent = `Save ${kind} ↗`;
 const txn = kind === 'transaction'; $('#transaction-fields').hidden = !txn; $('#description').required = txn; $('#date').required = txn;
 $('#description').disabled = !txn; $('#date').disabled = !txn; $('#type').disabled = !txn;
 $('#budget-month-note').hidden = txn; $('#budget-month-note').textContent = `Budget for ${monthName()}. Expenses in this category count toward it.`;
 $('#amount').value = record?.amount ?? ''; $('#category').value = record?.categoryId || (txn ? 3 : 3);
 if (txn) { $('#description').value = record?.description || ''; $('#type').value = record?.type || 'EXPENSE'; $('#date').value = record?.date || (localDate().startsWith($('#month').value) ? localDate() : $('#month').value+'-01'); }
 $('#edit-dialog').showModal();
}
['#add-transaction','#dock-add'].forEach((id) => $(id).addEventListener('click', () => openEditor('transaction')));
['#add-budget','#quick-budget'].forEach((id) => $(id).addEventListener('click', () => openEditor('budget')));
$('#close-dialog').addEventListener('click', () => $('#edit-dialog').close());
$('#edit-form').addEventListener('submit', async (event) => {
 event.preventDefault(); $('#save-record').disabled = true; $('#edit-error').textContent = '';
 try {
  const { kind, id, month } = state.editing; const body = { amount: Number($('#amount').value), categoryId: Number($('#category').value) };
  if (kind === 'transaction') Object.assign(body,{ description: $('#description').value.trim(), type: $('#type').value, date: $('#date').value }); else body.month = month;
  await api(`/api/${kind === 'transaction' ? 'transactions' : 'budgets'}${id ? '/'+id : ''}`,{method:id ? 'PUT' : 'POST',body});
  if (kind === 'transaction') $('#month').value = body.date.slice(0,7);
  $('#edit-dialog').close(); toast(`${kind === 'transaction' ? 'Transaction' : 'Budget'} saved.`); await loadMonth();
 } catch (error) { $('#edit-error').textContent = error.message; } finally { $('#save-record').disabled = false; }
});
function confirmAction(title,message,label) {
 return new Promise((resolve) => {
  const dialog = $('#confirm-dialog'); $('#confirm-title').textContent = title; $('#confirm-message').textContent = message; $('#accept-confirm').textContent = label;
  const finish = (value) => { dialog.close(); $('#accept-confirm').onclick = null; $('#cancel-confirm').onclick = null; dialog.oncancel = null; resolve(value); };
  $('#accept-confirm').onclick = () => finish(true); $('#cancel-confirm').onclick = () => finish(false); dialog.oncancel = (event) => { event.preventDefault(); finish(false); }; dialog.showModal();
 });
}
document.addEventListener('click', async (event) => {
 const button = event.target.closest('[data-edit-transaction],[data-delete-transaction],[data-edit-budget],[data-delete-budget]'); if (!button) return;
 for (const kind of ['transaction','budget']) {
  const edit = button.dataset[`edit${kind[0].toUpperCase()+kind.slice(1)}`], remove = button.dataset[`delete${kind[0].toUpperCase()+kind.slice(1)}`];
  if (edit) openEditor(kind,Number(edit));
  if (remove && await confirmAction(`Delete this ${kind}?`,'This permanently removes the record from your account.','Delete')) {
   try { await api(`/api/${kind === 'transaction' ? 'transactions' : 'budgets'}/${remove}`,{method:'DELETE'}); toast('Record deleted.'); await loadMonth(); } catch (error) { toast(error.message); }
  }
 }
});
$('#export-csv').addEventListener('click', () => {
 const cell = (value) => '"'+String(value).replace(/^[=+@\-\t\r]/, (c) => "'"+c).replace(/"/g,'""')+'"';
 const lines = [['Date','Description','Category','Type','Amount (USD)'],...state.transactions.map((t) => [t.date,t.description,categoryName(t.categoryId),t.type,t.amount])];
 const url = URL.createObjectURL(new Blob(['\ufeff'+lines.map((row) => row.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));
 const link = document.createElement('a'); link.href = url; link.download = `still-transactions-${$('#month').value}.csv`; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000); toast('Your CSV is ready.');
});
$('#sample-data').addEventListener('click', async () => {
 if (!await confirmAction('Try a sample month?','This adds fictional transactions and budgets to the selected month in your account. You can edit or delete each one.','Add sample data')) return;
 $('#sample-data').disabled = true;
 try { await api('/api/demo?month='+$('#month').value,{method:'POST'}); await loadMonth(); toast('Sample month added. Make yourself at home.'); } catch (error) { toast(error.message); } finally { $('#sample-data').disabled = false; }
});
(async () => { try { await refreshCsrf(); await enterApp(); } catch (error) { showAuth(); if (!error.message.includes('sign in')) $('#auth-error').textContent = error.message; } })();
