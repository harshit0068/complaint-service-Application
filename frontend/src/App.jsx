import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, Bell, Building2,
  Check, CheckCheck, ChevronDown, CircleHelp, ClipboardList, Clock3,
  FileText, Filter, Home, LogOut, Plus, RefreshCw, Search, Settings2, ShieldCheck,
  Sparkles, Star, Users, Wrench, X,
} from "lucide-react";

const USER_API = import.meta.env.VITE_USER_API || "http://localhost:8082/api";
const COMPLAINT_API = import.meta.env.VITE_COMPLAINT_API || "http://localhost:8081/api";
const CATEGORIES = ["Plumbing", "Electrical", "Wi-Fi", "Mess", "Cleaning", "Furniture", "Other"];
const STAFF_SPECIALTIES = ["Plumbing", "Electrical", "Wi-Fi", "Cleaning", "Mess", "Furniture", "General maintenance"];
const BLOCKS = ["A Block", "B Block", "C Block", "D Block", "E Block"];

async function api(base, path, token, options = {}) {
  const headers = new Headers(options.headers || {});
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${base}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      message = data.detail || data.message || data.error || message;
    } catch {
      // Keep the HTTP status message when the response has no JSON body.
    }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

const readSession = () => {
  try {
    return JSON.parse(localStorage.getItem("hostelfix-session") || "null");
  } catch {
    return null;
  }
};

const dateLabel = (value) =>
  new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" }).format(new Date(value));

const initials = (name = "") =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase();

function App() {
  const [session, setSession] = useState(readSession);
  const [page, setPage] = useState("overview");
  const [complaints, setComplaints] = useState([]);
  const [staff, setStaff] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [reports, setReports] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const user = session?.user;
  const isManager = user && ["WARDEN", "ADMIN"].includes(user.role);
  const isStudent = user?.role === "STUDENT";
  const isStaff = user?.role === "STAFF";

  const loadWorkspace = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError("");
    try {
      const results = await Promise.all([
        api(COMPLAINT_API, "/complaints", session.token),
        api(USER_API, "/notifications", session.token),
      ]);
      setComplaints(results[0]);
      setNotifications(results[1]);
      if (isManager) {
        const [people, summary] = await Promise.all([
          api(USER_API, "/users/staff", session.token),
          api(COMPLAINT_API, "/reports", session.token),
        ]);
        setStaff(people);
        setReports(summary);
      }
    } catch (requestError) {
      setError(requestError.message);
      if (requestError.message.includes("401")) {
        signOut();
      }
    } finally {
      setLoading(false);
    }
  }, [isManager, session]);

  useEffect(() => {
    loadWorkspace();
  }, [loadWorkspace]);

  function signIn(data) {
    localStorage.setItem("hostelfix-session", JSON.stringify(data));
    setSession(data);
    setPage("overview");
    setError("");
  }

  function signOut() {
    localStorage.removeItem("hostelfix-session");
    setSession(null);
    setComplaints([]);
    setNotifications([]);
    setReports(null);
    setPage("overview");
  }

  const filteredComplaints = useMemo(() => complaints.filter((complaint) => {
    const matchesStatus = statusFilter === "ALL" || complaint.status === statusFilter;
    const haystack = `${complaint.id} ${complaint.category} ${complaint.block} ${complaint.description} ${complaint.studentName}`
      .toLowerCase();
    return matchesStatus && haystack.includes(search.toLowerCase());
  }), [complaints, search, statusFilter]);

  if (!session) return <AuthScreen onSignIn={signIn} />;

  async function submitComplaint(form) {
    const payload = new FormData();
    payload.append("complaint", new Blob([JSON.stringify({
      category: form.category, block: form.block, description: form.description,
    })], { type: "application/json" }));
    if (form.photo) payload.append("photo", form.photo);
    await api(COMPLAINT_API, "/complaints", session.token, { method: "POST", body: payload });
    await loadWorkspace();
    setPage("complaints");
  }

  async function updateStatus(complaint, status, resolutionNote) {
    await api(COMPLAINT_API, `/complaints/${complaint.id}/status`, session.token, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status, resolutionNote }),
    });
    await loadWorkspace();
  }

  async function assignComplaint(complaint, selectedId) {
    const assigned = staff.find((member) => String(member.id) === selectedId);
    if (!assigned) return;
    await api(COMPLAINT_API, `/complaints/${complaint.id}/assign`, session.token, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ staffId: assigned.id, staffName: assigned.name }),
    });
    await loadWorkspace();
  }

  async function rateComplaint(complaint, rating) {
    await api(COMPLAINT_API, `/complaints/${complaint.id}/rating`, session.token, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rating: Number(rating), feedback: "" }),
    });
    await loadWorkspace();
  }

  async function createStaff(form) {
    await api(USER_API, "/users/staff", session.token, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(form),
    });
    await loadWorkspace();
  }

  async function updateStaffSpecialty(member, specialty) {
    try {
      await api(USER_API, `/users/staff/${member.id}/specialty`, session.token, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ specialty }),
      });
      await loadWorkspace();
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function markNotificationRead(item) {
    await api(USER_API, `/notifications/${item.id}/read`, session.token, { method: "PATCH" });
    await loadWorkspace();
  }

  const navItems = [
    { id: "overview", label: "Overview", icon: Home },
    { id: "complaints", label: isStaff ? "Assigned complaints" : "Complaints", icon: ClipboardList, count: complaints.length },
    ...(isStudent ? [{ id: "new", label: "New complaint", icon: Plus }] : []),
    ...(isManager ? [{ id: "reports", label: "Reports", icon: Activity }] : []),
    ...(isManager ? [{ id: "team", label: "People & team", icon: Users }] : []),
    { id: "notifications", label: "Notifications", icon: Bell, count: notifications.filter((item) => !item.read).length },
  ];

  const heading = navItems.find((item) => item.id === page)?.label || "Overview";
  const openCount = complaints.filter((item) => item.status !== "RESOLVED").length;
  const resolvedCount = complaints.filter((item) => item.status === "RESOLVED").length;
  const avgRating = complaints.filter((item) => item.rating).length
    ? (complaints.reduce((sum, item) => sum + (item.rating || 0), 0) /
      complaints.filter((item) => item.rating).length).toFixed(1)
    : "—";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" onClick={(event) => { event.preventDefault(); setPage("overview"); }}>
          <span className="brand-mark"><Building2 size={20} strokeWidth={2.2} /></span>
          <span><strong>hostel<span>fix</span></strong><small>RESIDENCE CARE</small></span>
        </a>
        <div className="nav-caption">WORKSPACE</div>
        <nav className="side-nav" aria-label="Main navigation">
          {navItems.map(({ id, label, icon: Icon, count }) => (
            <button className={`nav-link ${page === id ? "active" : ""}`} key={id} onClick={() => setPage(id)}>
              <Icon size={18} strokeWidth={1.9} />
              <span>{label}</span>
              {count > 0 && <small>{id === "notifications" ? count : count > 99 ? "99+" : count}</small>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card">
            <span className="help-icon"><CircleHelp size={17} /></span>
            <div><strong>Need a hand?</strong><p>Your residence team is here for you.</p></div>
            <ArrowRight size={15} />
          </div>
          <div className="sidebar-user">
            <Avatar name={user.name} />
            <span className="user-lines"><strong>{user.name}</strong><small>{roleLabel(user.role)}</small></span>
            <button className="icon-button" aria-label="Sign out" title="Sign out" onClick={signOut}><LogOut size={17} /></button>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumbs"><span>HostelFix</span><span className="crumb-sep">/</span><strong>{heading}</strong></div>
          <div className="topbar-right">
            <span className="live-indicator"><i /> All systems operational</span>
            <button className="top-avatar" title={`${user.name} · ${roleLabel(user.role)}`}><Avatar name={user.name} small /></button>
            <button className="topbar-signout" type="button" onClick={signOut}>
              <LogOut size={15} />
              <span>Sign out</span>
            </button>
          </div>
        </header>
        <div className="page-wrap">
          {error && <div className="alert error-alert"><X size={17} />{error}<button onClick={() => setError("")} aria-label="Dismiss"><X size={16} /></button></div>}
          {loading && <div className="loading-strip"><span /> Syncing your workspace…</div>}
          {page === "overview" && <Overview
            user={user} complaints={complaints} isManager={isManager} isStudent={isStudent} isStaff={isStaff}
            openCount={openCount} resolvedCount={resolvedCount} avgRating={avgRating}
            onNew={() => setPage("new")} onNavigate={setPage} onRefresh={loadWorkspace}
            filteredComplaints={filteredComplaints.slice(0, 5)} staff={staff}
            onAssign={assignComplaint} onStatus={updateStatus} onRate={rateComplaint}
          />}
          {page === "complaints" && <ComplaintsPage
            complaints={filteredComplaints} search={search} onSearch={setSearch}
            statusFilter={statusFilter} onStatusFilter={setStatusFilter}
            onNew={() => setPage("new")} isManager={isManager} isStaff={isStaff}
            isStudent={isStudent} staff={staff} onAssign={assignComplaint}
            onStatus={updateStatus} onRate={rateComplaint}
          />}
          {page === "new" && isStudent && <NewComplaint block={user.block} onSubmit={submitComplaint} />}
          {page === "reports" && isManager && <ReportsPage reports={reports} complaints={complaints} />}
          {page === "team" && isManager && <TeamPage staff={staff} onCreate={createStaff} onUpdateSpecialty={updateStaffSpecialty} />}
          {page === "notifications" && <NotificationsPage items={notifications} onRead={markNotificationRead} />}
        </div>
      </main>
    </div>
  );
}

function AuthScreen({ onSignIn }) {
  const [mode, setMode] = useState("login");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    const body = Object.fromEntries(form.entries());
    try {
      const result = await api(USER_API, mode === "login" ? "/auth/login" : "/auth/register", null, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      onSignIn(result);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-art">
        <div className="auth-art-top"><a className="brand brand-light" href="#"><span className="brand-mark"><Building2 size={20} /></span><span><strong>hostel<span>fix</span></strong><small>RESIDENCE CARE</small></span></a><span className="auth-edition">BUILT FOR BETTER LIVING</span></div>
        <div className="auth-story">
          <span className="eyebrow"><Sparkles size={14} /> YOUR RESIDENCE, IN GOOD HANDS</span>
          <h1>Small issues.<br /><em>Better handled.</em></h1>
          <p>One place to raise a concern, follow its progress, and help make hostel life a little better for everyone.</p>
          <div className="auth-quote">
            <div className="quote-avatars"><Avatar name="Mira Shah" tiny /><Avatar name="Arjun Rao" tiny /><Avatar name="Nia Patel" tiny /></div>
            <span><strong>Care that closes the loop.</strong><small>Made for the people who live here.</small></span><ArrowUpRight size={18} />
          </div>
        </div>
        <div className="art-decoration decor-one" /><div className="art-decoration decor-two" />
        <span className="auth-foot">A calmer way to care for your community.</span>
      </div>
      <div className="auth-panel">
        <div className="auth-mobile-brand"><Building2 size={20} /><strong>hostelfix</strong></div>
        <div className="auth-form-wrap">
          <span className="auth-overline">{mode === "login" ? "WELCOME BACK" : "JOIN YOUR RESIDENCE"}</span>
          <h2>{mode === "login" ? "Good to see you." : "Make yourself at home."}</h2>
          <p className="auth-subtitle">{mode === "login" ? "Sign in to pick up where you left off." : "Create your student account to get started."}</p>
          <div className="auth-tabs"><button className={mode === "login" ? "selected" : ""} onClick={() => { setMode("login"); setError(""); }}>Sign in</button><button className={mode === "register" ? "selected" : ""} onClick={() => { setMode("register"); setError(""); }}>Create account</button></div>
          {error && <div className="form-error">{error}</div>}
          <form onSubmit={submit} className="auth-form">
            {mode === "register" && <label>Full name<input name="name" placeholder="e.g. Aanya Sharma" autoComplete="name" required maxLength={100} /></label>}
            <label>College email<input name="email" type="email" placeholder="you@university.edu" autoComplete="email" required /></label>
            <label>Password<input name="password" type="password" placeholder={mode === "login" ? "Enter your password" : "At least 8 characters"} autoComplete={mode === "login" ? "current-password" : "new-password"} minLength={8} required /></label>
            {mode === "register" && <label>Hostel block<select name="block" defaultValue="" required><option value="" disabled>Select your block</option>{BLOCKS.map((block) => <option key={block}>{block}</option>)}</select></label>}
            {mode === "login" && <div className="form-meta"><span><ShieldCheck size={14} /> Secure student & staff access</span></div>}
            <button className="button primary full" type="submit" disabled={busy}>{busy ? "Please wait…" : mode === "login" ? "Sign in to HostelFix" : "Create student account"}<ArrowRight size={16} /></button>
          </form>
          <p className="auth-bottom-note"><ShieldCheck size={15} /> Your account and personal details stay protected.</p>
        </div>
        <span className="auth-copyright">© 2026 HostelFix · Residence care, made human.</span>
      </div>
    </div>
  );
}

function Overview({ user, complaints, isManager, isStudent, isStaff, openCount, resolvedCount, avgRating, onNew, onNavigate, onRefresh, filteredComplaints, staff, onAssign, onStatus, onRate }) {
  const greeting = new Intl.DateTimeFormat("en", { hour: "numeric", hour12: false }).format(new Date());
  const salutation = Number(greeting) < 12 ? "Good morning" : Number(greeting) < 17 ? "Good afternoon" : "Good evening";
  const firstName = user.name.split(" ")[0];
  const displayComplaints = filteredComplaints.length ? filteredComplaints : complaints.slice(0, 5);
  const assignedCount = complaints.filter((item) => item.status === "ASSIGNED").length;
  const inProgressCount = complaints.filter((item) => item.status === "IN_PROGRESS").length;
  return (
    <>
      <section className="welcome-row">
        <div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> {isStaff ? "YOUR ASSIGNED WORK" : "YOUR HOSTEL, IN THE LOOP"}</div><h1>{salutation}, {firstName}<span className="wave">✳</span></h1><p>{isStaff ? "Review your assigned complaints, update progress, and record completed fixes." : "Here’s what’s happening around your residence today."}</p></div>
        {isStudent && <button className="button primary" onClick={onNew}><Plus size={17} /> Raise a complaint</button>}
        {isStaff && <button className="button primary" onClick={() => onNavigate("complaints")}><ClipboardList size={16} /> View assigned work</button>}
      </section>
      <section className="stat-grid">
        <StatCard label={isStaff ? "ASSIGNED TO YOU" : isStudent ? "YOUR OPEN ISSUES" : "NEEDS ATTENTION"} value={openCount} hint={isStaff ? "Complaints still needing action" : openCount ? "Being worked on" : "You're all caught up"} icon={ClipboardList} tone="mint" trend={openCount ? "Open work" : "Looking good"} />
        <StatCard label={isStaff ? "IN PROGRESS" : "RESOLVED"} value={isStaff ? inProgressCount : resolvedCount} hint={isStaff ? "Work actively underway" : complaints.length ? `${Math.round((resolvedCount / complaints.length) * 100)}% of all complaints` : "A fresh start"} icon={CheckCheck} tone="lavender" trend={isStaff ? "In progress" : "Closed loop"} />
        <StatCard label={isStaff ? "AWAITING START" : "TOTAL REPORTED"} value={isStaff ? assignedCount : complaints.length} hint={isStaff ? "Assigned and ready to begin" : isStudent ? "Your reported concerns" : "Across your residence"} icon={Activity} tone="sand" trend={isStaff ? "Assigned" : isManager ? "All blocks" : "This semester"} />
        <StatCard label={isStaff ? "COMPLETED" : isStudent ? "YOUR RATING" : "STUDENT RATING"} value={isStaff ? resolvedCount : avgRating} hint={isStaff ? "Closed with a resolution note" : "Out of 5.0 stars"} icon={Star} tone="peach" trend={isStaff ? "Resolved" : "Feedback matters"} />
      </section>
      <section className="content-grid">
        <div className="panel recent-panel">
          <div className="panel-heading"><div><span className="section-kicker">{isStaff ? "YOUR WORK QUEUE" : "THE LATEST"}</span><h2>{isStaff ? "Assigned complaints" : "Recent complaints"}</h2></div><div className="panel-actions">{isStaff && <button className="text-link" onClick={onRefresh}><RefreshCw size={13} /> Refresh</button>}<button className="text-link" onClick={() => onNavigate("complaints")}>{isStaff ? "View assigned work" : "View all"} <ArrowRight size={15} /></button></div></div>
          {displayComplaints.length === 0 ? <EmptyState title={isStaff ? "No complaints assigned yet." : "A clear slate."} text={isStaff ? "New work assigned by your warden will appear here." : "No complaints have been raised yet."} action={isStudent ? "Raise your first complaint" : undefined} onClick={onNew} /> : <ComplaintTable
            complaints={displayComplaints} isManager={isManager} isStaff={user.role === "STAFF"} isStudent={isStudent}
            staff={staff} compact onAssign={onAssign} onStatus={onStatus} onRate={onRate}
          />}
        </div>
        <div className="right-rail">
          <div className="panel pulse-panel">
            <div className="panel-heading"><div><span className="section-kicker">THE BIG PICTURE</span><h2>Residence pulse</h2></div><span className="pulse-icon"><Activity size={17} /></span></div>
            {isManager && <BlockPulse complaints={complaints} />}
            {!isManager && <div className="pulse-summary"><div className="pulse-circle"><strong>{complaints.length ? Math.round(resolvedCount / complaints.length * 100) : 0}<small>%</small></strong></div><div><strong>Issues resolved</strong><p>{resolvedCount} closed {openCount ? `· ${openCount} still open` : "so far"}</p></div></div>}
            <button className="soft-link" onClick={() => onNavigate(isManager ? "reports" : "complaints")}>{isManager ? "Explore all reports" : "See your progress"}<ArrowRight size={15} /></button>
          </div>
          <div className="tip-card"><span className="tip-spark"><Sparkles size={15} /></span><span className="section-kicker">A LITTLE REMINDER</span><h3>Good feedback helps everyone.</h3><p>Once an issue is resolved, take a moment to rate how it was handled.</p><button onClick={() => onNavigate("complaints")}>Share feedback <ArrowUpRight size={14} /></button></div>
        </div>
      </section>
    </>
  );
}

function StatCard({ label, value, hint, icon: Icon, tone, trend }) {
  return <article className="stat-card"><div className="stat-top"><span>{label}</span><span className={`stat-icon ${tone}`}><Icon size={17} strokeWidth={2} /></span></div><div className="stat-value">{value}</div><div className="stat-bottom"><span>{hint}</span><span className="stat-trend"><i />{trend}</span></div></article>;
}

function BlockPulse({ complaints }) {
  const blocks = BLOCKS.map((block) => ({
    name: block.replace(" Block", ""),
    count: complaints.filter((item) => item.block === block && item.status !== "RESOLVED").length,
  }));
  const max = Math.max(...blocks.map((block) => block.count), 1);
  return <div className="block-pulse">{blocks.map((block) => <div className="block-row" key={block.name}><span className="block-label">Block {block.name}</span><span className="block-track"><i style={{ width: `${Math.max(block.count / max * 100, block.count ? 12 : 0)}%` }} /></span><strong>{block.count}</strong></div>)}</div>;
}

function ComplaintsPage({ complaints, search, onSearch, statusFilter, onStatusFilter, onNew, isManager, isStaff, isStudent, staff, onAssign, onStatus, onRate }) {
  return (
    <>
      <section className="page-heading-row"><div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> {isStaff ? "YOUR WORK QUEUE" : "YOUR RESIDENCE, AT A GLANCE"}</div><h1>{isStaff ? "Assigned complaints" : "Complaints"} <span className="heading-count">{complaints.length}</span></h1><p>{isStaff ? "Update each assignment as work progresses. Add a resolution note before closing an issue." : "Every issue, in one place. Follow along as the team gets things sorted."}</p></div>{isStudent && <button className="button primary" onClick={onNew}><Plus size={17} /> New complaint</button>}</section>
      <div className="panel complaints-panel">
        <div className="toolbar"><div className="searchbox"><Search size={16} /><input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search by issue, block or category…" /></div><label className="filter-select"><Filter size={15} /><select value={statusFilter} onChange={(event) => onStatusFilter(event.target.value)}><option value="ALL">All statuses</option><option value="OPEN">Open</option><option value="ASSIGNED">Assigned</option><option value="IN_PROGRESS">In progress</option><option value="RESOLVED">Resolved</option></select><ChevronDown size={15} /></label></div>
        <ComplaintTable complaints={complaints} isManager={isManager} isStaff={isStaff} isStudent={isStudent} staff={staff} onAssign={onAssign} onStatus={onStatus} onRate={onRate} />
      </div>
      <div className="table-footnote"><span><ShieldCheck size={14} /> Your complaint details are only visible to you and your residence team.</span><button onClick={onNew} disabled={!isStudent}>Need help? <ArrowRight size={14} /></button></div>
    </>
  );
}

function ComplaintTable({ complaints, isManager, isStaff, isStudent, staff, onAssign, onStatus, onRate, compact = false }) {
  if (!complaints.length) return <EmptyState title="No complaints to show." text="When an issue is raised, it will appear here." />;
  return (
    <div className={`table-scroll ${compact ? "compact-table" : ""}`}>
      <table><thead><tr><th>ISSUE</th><th>REPORTED BY</th><th>STATUS</th><th>DATE</th><th className="action-head">{compact ? "" : "ACTION"}</th></tr></thead>
        <tbody>{complaints.map((complaint) => <ComplaintRow key={complaint.id} complaint={complaint} isManager={isManager} isStaff={isStaff} isStudent={isStudent} staff={staff} onAssign={onAssign} onStatus={onStatus} onRate={onRate} compact={compact} />)}</tbody>
      </table>
    </div>
  );
}

function ComplaintRow({ complaint, isManager, isStaff, isStudent, staff, onAssign, onStatus, onRate, compact }) {
  const [note, setNote] = useState(complaint.resolutionNote || "");
  const [confirmingResolution, setConfirmingResolution] = useState(false);
  const [busy, setBusy] = useState(false);
  const [rowError, setRowError] = useState("");
  async function run(action) {
    setBusy(true);
    setRowError("");
    try { await action(); } catch (error) { setRowError(error.message); } finally { setBusy(false); }
  }
  function handleStatusChange(status) {
    setRowError("");
    if (status === "RESOLVED") {
      setConfirmingResolution(true);
      return;
    }
    setConfirmingResolution(false);
    run(() => onStatus(complaint, status, note));
  }
  function confirmResolution() {
    if (!note.trim()) {
      setRowError("Add a resolution note before confirming.");
      return;
    }
    run(async () => {
      await onStatus(complaint, "RESOLVED", note);
      setConfirmingResolution(false);
    });
  }
  const canUpdate = isManager || (isStaff && complaint.assignedStaffId != null);
  return (
    <tr>
      <td><div className="issue-cell"><span className={`category-icon ${categoryTone(complaint.category)}`}>{categoryIcon(complaint.category)}</span><span><strong>#{String(complaint.id).padStart(4, "0")} · {complaint.category}</strong><small>{complaint.block} <span>·</span> {truncate(complaint.description, 56)}</small></span></div>{complaint.photoUrl && <a className="photo-link" href={`${COMPLAINT_API.replace(/\/api$/, "")}${complaint.photoUrl}`} target="_blank" rel="noreferrer">View photo <ArrowUpRight size={12} /></a>}</td>
      <td><div className="reporter-cell"><Avatar name={complaint.studentName} tiny /><span>{complaint.studentName}</span></div>{complaint.assignedStaffName && <small className="assigned-line">With {complaint.assignedStaffName}</small>}</td>
      <td><StatusBadge status={complaint.status} /></td>
      <td><span className="date-cell">{dateLabel(complaint.createdAt)}</span>{complaint.status === "RESOLVED" && complaint.rating && <small className="rating-line"><Star size={12} fill="currentColor" /> {complaint.rating}.0 student rating</small>}</td>
      <td className="action-cell">
        {isManager && staff.length > 0 && <select className="row-select" aria-label={`Assign complaint ${complaint.id}`} value={complaint.assignedStaffId || ""} onChange={(event) => run(() => onAssign(complaint, event.target.value))} disabled={busy}><option value="">Assign staff</option>{staff.map((member) => <option value={member.id} key={member.id}>{member.name} · {member.specialty || "General maintenance"}</option>)}</select>}
        {canUpdate && <div className="row-actions"><select className="row-select" aria-label={`Update status for complaint ${complaint.id}`} value={complaint.status} onChange={(event) => handleStatusChange(event.target.value)} disabled={busy}><option value="OPEN">Open</option><option value="ASSIGNED">Assigned</option><option value="IN_PROGRESS">In progress</option><option value="RESOLVED">Resolved</option></select>{complaint.status !== "RESOLVED" && <><input className="resolution-input" aria-label="Resolution note" placeholder={confirmingResolution ? "Required: describe the fix…" : "Add a note to close…"} value={note} maxLength={2000} onChange={(event) => { setNote(event.target.value); setRowError(""); }} />{confirmingResolution && <button className="resolve-button" type="button" onClick={confirmResolution} disabled={busy}><Check size={12} /> Confirm resolution</button>}</>}</div>}
        {isStudent && complaint.status === "RESOLVED" && !complaint.rating && <select className="row-select rating-select" aria-label={`Rate complaint ${complaint.id}`} defaultValue="" onChange={(event) => event.target.value && run(() => onRate(complaint, event.target.value))} disabled={busy}><option value="">Rate resolution…</option>{[5, 4, 3, 2, 1].map((rate) => <option value={rate} key={rate}>{rate} {rate === 1 ? "star" : "stars"}</option>)}</select>}
        {rowError && <small className="row-error">{rowError}</small>}
        {!isManager && !isStaff && (!isStudent || complaint.status !== "RESOLVED" || complaint.rating) && !compact && <span className="no-action">—</span>}
        {compact && !canUpdate && !(isStudent && complaint.status === "RESOLVED" && !complaint.rating) && <ArrowRight className="row-arrow" size={15} />}
      </td>
    </tr>
  );
}

function NewComplaint({ block, onSubmit }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [photoName, setPhotoName] = useState("");
  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    const file = data.get("photo");
    try {
      await onSubmit({
        category: data.get("category"),
        block: data.get("block"),
        description: data.get("description"),
        photo: file?.size ? file : null,
      });
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="page-heading-row"><div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> WE’LL TAKE IT FROM HERE</div><h1>Raise an issue</h1><p>Tell us what needs attention. Your residence team will take it from here.</p></div><span className="form-step">01 <i /> 02 <small>· just a minute</small></span></section>
      <div className="form-layout">
        <form className="panel complaint-form" onSubmit={handleSubmit}>
          {error && <div className="form-error">{error}</div>}
          <div className="form-section-title"><span>01</span><div><strong>What’s going on?</strong><small>A little detail helps us get the right person on it.</small></div></div>
          <div className="form-row"><label>Issue category<select name="category" defaultValue="" required><option value="" disabled>Choose a category</option>{CATEGORIES.map((category) => <option key={category}>{category}</option>)}</select></label><label>Your registered hostel block<input name="block" value={block || ""} readOnly required /><small>This is the block on your student account.</small></label></div>
          <label>Describe the issue<textarea name="description" rows="5" maxLength={2000} placeholder="What happened? Where should the team take a look? Add any helpful details…" required /></label>
          <div className="form-section-title photo-title"><span>02</span><div><strong>Add a photo <em>optional</em></strong><small>A picture can help the team understand it faster.</small></div></div>
          <label className="upload-box"><input name="photo" type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={(event) => setPhotoName(event.target.files?.[0]?.name || "")} /><span className="upload-icon"><Plus size={18} /></span><span><strong>{photoName || "Choose a photo to attach"}</strong><small>JPG, PNG, WEBP or GIF · up to 5 MB</small></span><span className="upload-action">Browse files</span></label>
          <div className="form-submit-row"><span><ShieldCheck size={15} /> Only your residence team can see this.</span><button className="button primary" disabled={busy}>{busy ? "Sending…" : "Send complaint"}<ArrowRight size={16} /></button></div>
        </form>
        <aside className="form-aside"><div className="aside-illustration"><Wrench size={24} /><span><Check size={13} /></span></div><span className="section-kicker">WHAT HAPPENS NEXT</span><h3>We’ll keep you in the loop.</h3><ol className="steps-list"><li><span>1</span><div><strong>Your team gets the details</strong><small>We’ll route your concern to the right people.</small></div></li><li><span>2</span><div><strong>Follow along as it moves</strong><small>See when someone picks it up and starts work.</small></div></li><li><span>3</span><div><strong>Close the loop together</strong><small>Rate the fix once everything is sorted.</small></div></li></ol><div className="aside-note"><Sparkles size={14} /> A thoughtful note helps us help you faster.</div></aside>
      </div>
    </>
  );
}

function ReportsPage({ reports, complaints }) {
  if (!reports) return <div className="panel"><EmptyState title="Reports are loading." text="Refresh in a moment to see the latest residence insights." /></div>;
  const categories = Object.entries(reports.averageResolutionMinutes || {}).sort((a, b) => a[1] - b[1]);
  const blocks = Object.entries(reports.pendingByBlock || {}).sort((a, b) => b[1] - a[1]);
  const maxMinutes = Math.max(...categories.map(([, value]) => value), 1);
  const maxPending = Math.max(...blocks.map(([, value]) => value), 1);
  return (
    <>
      <section className="page-heading-row"><div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> A CLEARER VIEW OF CARE</div><h1>Residence insights</h1><p>See where support is needed, and how quickly issues are getting resolved.</p></div><span className="report-updated"><i /> Live from your complaints</span></section>
      <section className="report-kpis"><StatCard label="ISSUES IN THE SYSTEM" value={reports.total} hint="All reported concerns" icon={ClipboardList} tone="mint" trend="All blocks" /><StatCard label="STILL NEED ATTENTION" value={reports.pending} hint="Not yet resolved" icon={Clock3} tone="sand" trend="In the queue" /><StatCard label="CLOSED THE LOOP" value={reports.resolved} hint="Successfully resolved" icon={CheckCheck} tone="lavender" trend={reports.total ? `${Math.round(reports.resolved / reports.total * 100)}% resolution` : "New report"} /><StatCard label="CATEGORIES TRACKED" value={categories.length} hint="With completed resolutions" icon={Activity} tone="peach" trend="By issue type" /></section>
      <div className="reports-grid">
        <section className="panel report-panel"><div className="panel-heading"><div><span className="section-kicker">TIME TO MAKE IT RIGHT</span><h2>Resolution by category</h2></div><span className="report-icon mint"><Clock3 size={17} /></span></div><p className="report-description">Average time from the moment an issue is raised to when it’s resolved.</p>
          {categories.length ? <div className="bar-list">{categories.map(([category, minutes]) => <div className="bar-item" key={category}><div><span>{category}</span><strong>{formatMinutes(minutes)}</strong></div><i><span style={{ width: `${Math.max(minutes / maxMinutes * 100, 4)}%` }} /></i></div>)}</div> : <EmptyState title="No completed issues yet." text="Resolution times will show here once the team closes a complaint." />}
        </section>
        <section className="panel report-panel"><div className="panel-heading"><div><span className="section-kicker">AROUND THE RESIDENCE</span><h2>Open issues by block</h2></div><span className="report-icon sand"><Building2 size={17} /></span></div><p className="report-description">Where your team’s attention is needed right now.</p>
          {blocks.length ? <div className="block-report-list">{blocks.map(([block, count]) => <div className="block-report" key={block}><div className="block-report-top"><span><Building2 size={15} />{block}</span><strong>{count} <small>{count === 1 ? "issue" : "issues"}</small></strong></div><span className="block-report-track"><i style={{ width: `${Math.max(count / maxPending * 100, 8)}%` }} /></span></div>)}</div> : <EmptyState title="Every block is clear." text="There are no pending issues to follow up on." />}
        </section>
      </div>
      <div className="report-foot"><span><ShieldCheck size={15} /> Reports are based on the latest complaint updates.</span><span><FileText size={15} /> {complaints.length} records in view</span></div>
    </>
  );
}

function TeamPage({ staff, onCreate, onUpdateSpecialty }) {
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(true);
    setError("");
    const data = new FormData(form);
    try {
      await onCreate(Object.fromEntries(data.entries()));
      form.reset();
      setShowForm(false);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="page-heading-row"><div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> THE PEOPLE BEHIND THE FIXES</div><h1>Your residence team</h1><p>Manage the staff members who help keep your hostel running smoothly.</p></div><button className="button primary" onClick={() => setShowForm(!showForm)}><Plus size={17} /> Add staff member</button></section>
      {showForm && <form className="panel staff-form" onSubmit={submit}>{error && <div className="form-error">{error}</div>}<div className="form-row"><label>Full name<input name="name" placeholder="e.g. Ravi Kumar" required maxLength={100} /></label><label>Email address<input name="email" type="email" placeholder="ravi@university.edu" required /></label><label>Work specialty<select name="specialty" defaultValue="" required><option value="" disabled>Choose a specialty</option>{STAFF_SPECIALTIES.map((specialty) => <option key={specialty}>{specialty}</option>)}</select></label><label>Temporary password<input name="password" type="password" minLength={8} maxLength={72} required /></label></div><div className="staff-submit"><span>Choose the staff member’s main work specialty. Share the temporary password securely.</span><button className="button primary" disabled={busy}>{busy ? "Creating…" : "Create staff account"}<ArrowRight size={15} /></button></div></form>}
      <div className="team-summary"><div className="team-count"><Users size={17} /><strong>{staff.length}</strong><span>active staff {staff.length === 1 ? "member" : "members"}</span></div><span><i /> Assign staff to a complaint from the Complaints page.</span></div>
      <section className="team-grid">{staff.map((member, index) => <article className="panel team-card" key={member.id}><div className={`team-avatar team-avatar-${index % 4}`}><Avatar name={member.name} /></div><div className="team-details"><span className="team-role">WORK SPECIALTY</span><h3>{member.name}</h3><p>{member.email}</p><select className="team-specialty-select" aria-label={`Work specialty for ${member.name}`} value={member.specialty || ""} onChange={(event) => onUpdateSpecialty(member, event.target.value)}><option value="" disabled>Set work specialty</option>{STAFF_SPECIALTIES.map((specialty) => <option key={specialty}>{specialty}</option>)}</select></div><span className="team-status"><i /> Active</span></article>)}{staff.length === 0 && <div className="panel"><EmptyState title="Your team starts here." text="Add staff accounts so wardens can assign and track complaint work." action="Add a staff member" onClick={() => setShowForm(true)} /></div>}</section>
    </>
  );
}

function NotificationsPage({ items, onRead }) {
  return (
    <>
      <section className="page-heading-row"><div><div className="eyebrow green-eyebrow"><span className="eyebrow-dot" /> NOTHING IMPORTANT MISSED</div><h1>Your notifications</h1><p>Updates from your residence team, gathered in one place.</p></div><span className="notification-count">{items.filter((item) => !item.read).length} unread</span></section>
      <section className="panel notification-panel">{items.length ? items.map((item) => <article className={`notification-item ${item.read ? "" : "unread"}`} key={item.id}><span className="notification-icon"><Bell size={17} /></span><div className="notification-body"><div><strong>{item.title}</strong>{!item.read && <i />}</div><p>{item.message}</p><small>{dateLabel(item.createdAt)}</small></div>{!item.read && <button className="mark-read" onClick={() => onRead(item)}><Check size={14} /> Mark read</button>}</article>) : <EmptyState title="All quiet here." text="When your complaint changes status, you’ll find the update here." />}</section>
    </>
  );
}

function StatusBadge({ status }) {
  const labels = { OPEN: "Open", ASSIGNED: "Assigned", IN_PROGRESS: "In progress", RESOLVED: "Resolved" };
  return <span className={`status-badge status-${status?.toLowerCase()}`}><i />{labels[status] || status}</span>;
}

function Avatar({ name, small = false, tiny = false }) {
  return <span className={`avatar ${small ? "avatar-small" : ""} ${tiny ? "avatar-tiny" : ""}`} aria-hidden="true">{initials(name)}</span>;
}

function EmptyState({ title, text, action, onClick }) {
  return <div className="empty-state"><span className="empty-mark"><Sparkles size={18} /></span><strong>{title}</strong><p>{text}</p>{action && <button className="text-link" onClick={onClick}>{action}<ArrowRight size={15} /></button>}</div>;
}

function roleLabel(role) {
  return ({ STUDENT: "Student", STAFF: "Residence staff", WARDEN: "Residence warden", ADMIN: "Administrator" })[role] || role;
}

function categoryTone(category = "") {
  return ({ Plumbing: "tone-blue", Electrical: "tone-gold", "Wi-Fi": "tone-violet", Mess: "tone-green", Cleaning: "tone-coral", Furniture: "tone-cyan" })[category] || "tone-gray";
}

function categoryIcon(category = "") {
  return ({ Plumbing: "≈", Electrical: "ϟ", "Wi-Fi": "⌁", Mess: "✳", Cleaning: "✧", Furniture: "▤" })[category] || "•";
}

function truncate(value = "", limit = 60) {
  return value.length > limit ? `${value.slice(0, limit).trimEnd()}…` : value;
}

function formatMinutes(minutes) {
  if (minutes < 60) return `${Math.round(minutes)} min`;
  const hours = minutes / 60;
  if (hours < 48) return `${hours.toFixed(1)} hr`;
  return `${(hours / 24).toFixed(1)} days`;
}

export default App;
