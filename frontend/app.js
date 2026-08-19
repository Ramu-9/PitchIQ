// Firebase Configuration
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-app.js";
import { getAnalytics, logEvent } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-analytics.js";
import { getAuth, signInWithPopup, GoogleAuthProvider, onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-auth.js";

const API_BASE_URL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' 
    ? 'http://localhost:8080/api/v1' 
    : 'https://pitchiq-backend-402093814656.asia-south1.run.app/api/v1';

const firebaseConfig = {
    apiKey: "AIzaSyCRCMXsIdBYMUsa8Ec17kjtV-80YFvXVNI",
    authDomain: "pitchiq-5ed39.firebaseapp.com",
    projectId: "pitchiq-5ed39",
    storageBucket: "pitchiq-5ed39.firebasestorage.app",
    messagingSenderId: "832395208390",
    appId: "1:832395208390:web:712a543114c728bc98d9c7",
    measurementId: "G-JZX1DBCK6E"
};

const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();

// Log app open
logEvent(analytics, 'app_open');

// --- Authentication UI Logic ---
let currentUser = null;
let pendingAuthAction = null;
let guestUsage = parseInt(localStorage.getItem('pitchiq_guest_usage') || '0');

const authModal = document.getElementById('authModal');
const googleSignInBtn = document.getElementById('googleSignInBtn');
const maybeLaterBtn = document.getElementById('maybeLaterBtn');

const authProfileContainer = document.getElementById('authProfileContainer');
const compactProfile = document.getElementById('compactProfile');
const profileDropdown = document.getElementById('profileDropdown');

// UI Elements
const compactPhoto = document.getElementById('compactPhoto');
const compactName = document.getElementById('compactName');
const dropdownPhoto = document.getElementById('dropdownPhoto');
const dropdownName = document.getElementById('dropdownName');
const dropdownEmail = document.getElementById('dropdownEmail');
const signOutBtn = document.getElementById('signOutBtn');

// Listen for auth state changes
onAuthStateChanged(auth, (user) => {
    currentUser = user;
    if (user) {
        // Reset guest limits on successful login
        guestUsage = 0;
        localStorage.setItem('pitchiq_guest_usage', '0');
        
        // User is signed in
        const firstName = user.displayName ? user.displayName.split(' ')[0] : 'User';
        
        if (compactPhoto) compactPhoto.src = user.photoURL || '';
        compactName.textContent = firstName;
        dropdownPhoto.src = user.photoURL || '';
        dropdownName.textContent = user.displayName;
        dropdownEmail.textContent = user.email;

        document.getElementById('userStatusArea').classList.add('authenticated');

        // Reset dropdown state
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');

        // Show authenticated state immediately
        authProfileContainer.style.display = 'flex';
        compactProfile.classList.add('visible');

        // Execute any pending action
        if (pendingAuthAction) {
            const actionToRun = pendingAuthAction;
            pendingAuthAction = null;
            actionToRun();
        }
    } else {
        // User is signed out
        document.getElementById('userStatusArea').classList.remove('authenticated');
        authProfileContainer.style.display = 'none';
        compactProfile.classList.remove('visible');
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }
});

// Dropdown toggle
compactProfile.addEventListener('click', (e) => {
    e.stopPropagation();
    const isOpen = profileDropdown.style.display === 'flex';
    if (isOpen) {
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    } else {
        profileDropdown.style.display = 'flex';
        compactProfile.classList.add('open');
    }
});

// Close dropdown on outside click
document.addEventListener('click', () => {
    if (profileDropdown.style.display === 'flex') {
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }
});

profileDropdown.addEventListener('click', (e) => e.stopPropagation());

// Sign Out
signOutBtn.addEventListener('click', () => {
    signOut(auth).then(() => {
        logEvent(analytics, 'sign_out');
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }).catch(error => console.error("Sign out error", error));
});

// Auth Guard Function
function authGuard(actionCallback) {
    if (currentUser) {
        actionCallback();
    } else {
        pendingAuthAction = actionCallback;
        if (guestUsage === 0) {
            showAuthModal(false);
        } else if (guestUsage === 1) {
            // Second premium action: execute immediately without modal
            guestUsage = 2;
            localStorage.setItem('pitchiq_guest_usage', '2');
            pendingAuthAction = null;
            actionCallback();
        } else {
            // Third premium action and beyond: force login
            showAuthModal(true);
        }
    }
}

function showAuthModal(isForced) {
    logEvent(analytics, 'auth_modal_opened');
    document.getElementById('authErrorMsg').style.display = 'none';
    
    const helperMsg = document.getElementById('authHelperMsg');
    if (helperMsg) helperMsg.style.display = 'none';
    
    const subtitle = document.querySelector('.auth-subtitle');
    if (isForced) {
        subtitle.textContent = "You've explored PitchIQ as a guest. Sign in for the best experience and unlimited AI-powered cricket analytics.";
    } else {
        subtitle.textContent = "Sign in to unlock AI-powered cricket analytics.";
    }

    authModal.dataset.isForced = isForced ? 'true' : 'false';
    authModal.style.display = 'flex';
    setTimeout(() => { authModal.classList.add('visible'); }, 10);
}

// Modal Actions
maybeLaterBtn.addEventListener('click', () => {
    const isForced = authModal.dataset.isForced === 'true';
    if (isForced) {
        // Block action, keep modal open
        const helperMsg = document.getElementById('authHelperMsg');
        if (helperMsg) helperMsg.style.display = 'block';
    } else {
        logEvent(analytics, 'auth_modal_dismissed');
        authModal.classList.remove('visible');
        setTimeout(() => { authModal.style.display = 'none'; }, 400);
        
        // Mark first usage complete
        guestUsage = 1;
        localStorage.setItem('pitchiq_guest_usage', '1');
        
        if (pendingAuthAction) {
            const actionToRun = pendingAuthAction;
            pendingAuthAction = null;
            actionToRun();
        }
    }
});

const originalGoogleBtnHtml = googleSignInBtn.innerHTML;

googleSignInBtn.addEventListener('click', () => {
    const errorMsg = document.getElementById('authErrorMsg');
    errorMsg.style.display = 'none';
    googleSignInBtn.innerHTML = '<span class="pulse" style="margin-right: 8px;"></span> Signing in...';
    googleSignInBtn.disabled = true;

    signInWithPopup(auth, provider).then((result) => {
        logEvent(analytics, 'login');
        authModal.classList.remove('visible');
        setTimeout(() => { 
            authModal.style.display = 'none'; 
            googleSignInBtn.innerHTML = originalGoogleBtnHtml;
            googleSignInBtn.disabled = false;
        }, 400);
    }).catch((error) => {
        console.error("Sign-in failed", error);
        errorMsg.textContent = error.message || "Authentication failed. Please try again.";
        errorMsg.style.display = 'block';
        googleSignInBtn.innerHTML = originalGoogleBtnHtml;
        googleSignInBtn.disabled = false;
    });
});

// --- End Auth Logic ---

// Initialize ambient canvas background
const canvas = document.getElementById('ambientCanvas');
if (canvas) {
    const ctx = canvas.getContext('2d');
    let width, height;
    let particles = [];
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let animationFrameId;

    const resize = () => {
        width = window.innerWidth;
        height = document.documentElement.scrollHeight;
        canvas.width = width;
        canvas.height = height;
    };

    window.addEventListener('resize', resize);
    resize();

    class Particle {
        constructor() {
            this.reset(true);
        }

        reset(initial = false) {
            this.x = Math.random() * width;
            this.y = initial ? Math.random() * height : height + Math.random() * 60;
            this.size = Math.random() * 1.6 + 0.8;
            this.baseSpeedY = -(Math.random() * 0.12 + 0.03);
            this.baseSpeedX = (Math.random() - 0.5) * 0.08;
            this.opacity = Math.random() * 0.35 + 0.15;
            // Organic sinusoidal drift
            this.driftAmp = Math.random() * 0.3 + 0.1;
            this.driftFreq = Math.random() * 0.002 + 0.001;
            this.phase = Math.random() * Math.PI * 2;
            this.age = 0;
        }

        update() {
            if (prefersReducedMotion) return;
            this.age++;
            this.y += this.baseSpeedY;
            this.x += this.baseSpeedX + Math.sin(this.age * this.driftFreq + this.phase) * this.driftAmp;
            if (this.y < -20 || this.x < -20 || this.x > width + 20) {
                this.reset();
            }
        }

        draw() {
            ctx.save();
            ctx.beginPath();
            ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(0, 200, 224, ${this.opacity})`;
            ctx.shadowBlur = this.size * 3;
            ctx.shadowColor = `rgba(0, 200, 224, ${this.opacity * 0.6})`;
            ctx.fill();
            ctx.restore();
        }
    }

    const initParticles = () => {
        particles = [];
        const numParticles = prefersReducedMotion ? 0 : 50;
        for (let i = 0; i < numParticles; i++) {
            particles.push(new Particle());
        }
    };

    initParticles();

    let lastTime = 0;
    const fpsInterval = 1000 / 60;

    const animate = (time) => {
        animationFrameId = requestAnimationFrame(animate);

        const elapsed = time - lastTime;
        if (elapsed < fpsInterval) return;
        lastTime = time - (elapsed % fpsInterval);

        ctx.clearRect(0, 0, width, height);

        // Ambient radial lighting
        const gradient1 = ctx.createRadialGradient(width * 0.15, height * 0.15, 0, width * 0.15, height * 0.15, width * 0.7);
        gradient1.addColorStop(0, 'rgba(0, 200, 224, 0.04)');
        gradient1.addColorStop(1, 'transparent');
        ctx.fillStyle = gradient1;
        ctx.fillRect(0, 0, width, height);

        const gradient2 = ctx.createRadialGradient(width * 0.85, height * 0.85, 0, width * 0.85, height * 0.85, width * 0.6);
        gradient2.addColorStop(0, 'rgba(64, 128, 200, 0.03)');
        gradient2.addColorStop(1, 'transparent');
        ctx.fillStyle = gradient2;
        ctx.fillRect(0, 0, width, height);

        // Update and draw nodes
        particles.forEach(p => {
            p.update();
            p.draw();
        });

        // Draw neural connections
        ctx.save();
        ctx.lineWidth = 0.8;
        ctx.shadowBlur = 0;
        for (let i = 0; i < particles.length; i++) {
            for (let j = i + 1; j < particles.length; j++) {
                const p1 = particles[i];
                const p2 = particles[j];
                const dx = p1.x - p2.x;
                const dy = p1.y - p2.y;
                const dist = Math.sqrt(dx * dx + dy * dy);

                const maxDist = 180;
                if (dist < maxDist) {
                    const lineOpacity = (1 - dist / maxDist) * 0.25;
                    ctx.beginPath();
                    ctx.moveTo(p1.x, p1.y);
                    ctx.lineTo(p2.x, p2.y);
                    ctx.strokeStyle = `rgba(0, 200, 224, ${lineOpacity})`;
                    ctx.stroke();
                }
            }
        }
        ctx.restore();
    };

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            cancelAnimationFrame(animationFrameId);
        } else {
            lastTime = performance.now();
            animate(lastTime);
        }
    });

    if (!document.hidden) {
        animationFrameId = requestAnimationFrame(animate);
    }
}

// Manual Mode Toggle
const manualModeToggle = document.getElementById('manualModeToggle');
const inputFields = document.querySelectorAll('.locked-input');
const lockIcons = document.querySelectorAll('.lock-icon');

manualModeToggle.addEventListener('change', (e) => {
    const isManual = e.target.checked;
    
    // Always clear active match and default to T20 when entering manual mode 
    // to prevent format confusion from a previously clicked live match
    if (isManual) {
        document.querySelectorAll('.match-card.active').forEach(c => c.classList.remove('active'));
        document.getElementById('matchFormat').value = 't20';
        window.currentMatchVenue = '';
        window.currentBattingTeam = '';
        window.currentBowlingTeam = '';
        window.currentMatchStatus = 'live';
    }

    inputFields.forEach(input => {
        if (isManual) {
            input.removeAttribute('readonly');
            input.removeAttribute('disabled');
            input.classList.remove('locked-input');
        } else {
            input.setAttribute('readonly', true);
            input.setAttribute('disabled', true);
            input.classList.add('locked-input');
        }
    });
    lockIcons.forEach(icon => {
        icon.style.display = isManual ? 'none' : 'inline';
    });
});

// Clear validation message when user types
document.getElementById('oversBowled').addEventListener('input', () => {
    document.getElementById('validationMessage').style.display = 'none';
});
document.getElementById('matchFormat').addEventListener('change', () => {
    document.getElementById('validationMessage').style.display = 'none';
});

let _loadingEllipsisInterval = null;
let _loadingSafetyTimeout = null;
let _simTelemetryInterval = null;
let _simCounterRaf = null;
let _simFeedStageIdx = 0;

const SIM_TELEMETRY_STAGES = [
    "Initializing pitch model…",
    "Loading venue conditions…",
    "Calibrating scoring engine…",
    "Running Monte Carlo iterations…",
    "Sampling ball-by-ball outcomes…",
    "Computing run distributions…",
    "Evaluating wicket probabilities…",
    "Modeling powerplay dynamics…",
    "Analyzing death-over patterns…",
    "Estimating chase probability…",
    "Aggregating win probability…",
    "Converging simulation results…",
];

function _simAddFeedLine(text, cssClass) {
    const feed = document.getElementById('simTelemetryFeed');
    if (!feed) return;
    const line = document.createElement('div');
    line.className = 'sim-feed-line' + (cssClass ? ' ' + cssClass : '');
    line.innerHTML = `<span class="sim-feed-prefix">›</span> <span class="sim-feed-text">${text}</span>`;
    feed.appendChild(line);
    // Keep only last 6 lines visible
    while (feed.children.length > 6) {
        feed.removeChild(feed.firstChild);
    }
    feed.scrollTop = feed.scrollHeight;
}

function _simResetFeed() {
    const feed = document.getElementById('simTelemetryFeed');
    if (!feed) return;
    feed.innerHTML = '';
}

function _simStartTelemetry() {
    _simFeedStageIdx = 0;
    _simAddFeedLine(SIM_TELEMETRY_STAGES[0]);
    _simFeedStageIdx = 1;
    _simTelemetryInterval = setInterval(() => {
        if (_simFeedStageIdx < SIM_TELEMETRY_STAGES.length) {
            _simAddFeedLine(SIM_TELEMETRY_STAGES[_simFeedStageIdx]);
            _simFeedStageIdx++;
        } else {
            // Cycle back seamlessly from the computational middle
            _simFeedStageIdx = 3;
            _simAddFeedLine(SIM_TELEMETRY_STAGES[_simFeedStageIdx]);
            _simFeedStageIdx++;
        }
    }, 850);
}

function _simStopTelemetry() {
    if (_simTelemetryInterval) { clearInterval(_simTelemetryInterval); _simTelemetryInterval = null; }
}

function _simStartCounter() {
    const counter = document.getElementById('simCounter');
    if (!counter) return;
    let count = 0;
    const startTime = performance.now();
    const step = (timestamp) => {
        const elapsed = timestamp - startTime;
        // Asymptotic curve: starts fast, decelerates toward ~10,000
        // Mimics Monte Carlo convergence — rapid early progress, gradual refinement
        count = Math.floor(10000 * (1 - Math.exp(-elapsed / 2800)));
        counter.textContent = count.toLocaleString();
        _simCounterRaf = window.requestAnimationFrame(step);
    };
    _simCounterRaf = window.requestAnimationFrame(step);
}

function _simStopCounter() {
    if (_simCounterRaf) { window.cancelAnimationFrame(_simCounterRaf); _simCounterRaf = null; }
}

function _simCleanup() {
    _simStopTelemetry();
    _simStopCounter();
    if (_loadingEllipsisInterval) { clearInterval(_loadingEllipsisInterval); _loadingEllipsisInterval = null; }
    if (_loadingSafetyTimeout) { clearTimeout(_loadingSafetyTimeout); _loadingSafetyTimeout = null; }
}

function showLoadingSequence() {
    // Legacy function used by live match card click
    const overlay = document.getElementById('loadingOverlay');
    const text = document.getElementById('simStageText');
    const fill = document.getElementById('simProgressFill');
    const counterRow = document.querySelector('.sim-counter-row');
    const scannerGlow = document.getElementById('simScannerGlow');
    
    _simResetFeed();
    overlay.style.display = 'flex';
    setTimeout(() => { overlay.style.opacity = '1'; }, 10);
    
    if (counterRow) counterRow.style.display = 'none';
    if (scannerGlow) scannerGlow.classList.remove('sim-scanner-stopped');
    if (text) text.textContent = "LOADING MATCH DATA";
    if (fill) fill.style.width = "0%";
    _simAddFeedLine("Fetching live match data…");
}

function hideLoadingSequence() {
    _simCleanup();
    const overlay = document.getElementById('loadingOverlay');
    overlay.style.opacity = '0';
    setTimeout(() => { 
        overlay.style.display = 'none';
        const counterRow = document.querySelector('.sim-counter-row');
        if (counterRow) counterRow.style.display = 'flex';
        const counterDone = document.getElementById('simCounterDone');
        if (counterDone) counterDone.style.display = 'none';
        const scannerGlow = document.getElementById('simScannerGlow');
        if (scannerGlow) scannerGlow.classList.remove('sim-scanner-stopped');
    }, 500);
}

document.getElementById('analyzeBtn').addEventListener('click', () => {
    authGuard(async () => {
        logEvent(analytics, 'run_simulation');
        const btn = document.getElementById('analyzeBtn');
        
        // Prevent double clicks
        if (btn.disabled) return;
        btn.disabled = true;
    
    const matchFormat = document.getElementById('matchFormat').value;
    const oversInput = parseFloat(document.getElementById('oversBowled').value) || 0;

    // Clear validation message
    const validationMsg = document.getElementById('validationMessage');
    validationMsg.style.display = 'none';

    if (manualModeToggle.checked) {
        if (!document.getElementById('currentRuns').value || !document.getElementById('oversBowled').value) {
            validationMsg.textContent = "Please enter match state data before running simulation.";
            validationMsg.style.display = 'block';
            if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
            btn.disabled = false;
            return;
        }
    }

    // Validation Rules
    if (matchFormat === 't20' && oversInput > 20.0) {
        validationMsg.textContent = "Validation Error: Maximum overs for T20 is 20.0";
        validationMsg.style.display = 'block';
        if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
        btn.disabled = false;
        return;
    }
    if (matchFormat === 'odi' && oversInput > 50.0) {
        validationMsg.textContent = "Validation Error: Maximum overs for ODI is 50.0";
        validationMsg.style.display = 'block';
        if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
        btn.disabled = false;
        return;
    }

    try {
        // --- Telemetry Simulation UX Start ---
        const overlay = document.getElementById('loadingOverlay');
        const text = document.getElementById('simStageText');
        const fill = document.getElementById('simProgressFill');
        const counter = document.getElementById('simCounter');
        const counterDone = document.getElementById('simCounterDone');
        const counterRow = document.querySelector('.sim-counter-row');
        const counterLabel = document.getElementById('simCounterLabel');
        const scannerGlow = document.getElementById('simScannerGlow');
        
        // Reset overlay state
        _simCleanup();
        _simResetFeed();
        counter.textContent = "0";
        counterDone.style.display = "none";
        if (counterRow) counterRow.style.display = 'flex';
        if (counterLabel) counterLabel.textContent = 'iterations';
        fill.style.width = "0%";
        if (scannerGlow) scannerGlow.classList.remove('sim-scanner-stopped');
        
        overlay.style.display = 'flex';
        await new Promise(r => setTimeout(r, 10));
        overlay.style.opacity = '1';

        // Stage 1: Initialize engine
        text.textContent = "INITIALIZING ENGINE";
        _simAddFeedLine("Initializing PitchIQ telemetry engine…");
        await new Promise(r => setTimeout(r, 250));

        // Scroll into view so user sees the analytics section
        document.getElementById('hud').scrollIntoView({ behavior: 'smooth', block: 'start' });

        // Stage 2: Start live telemetry feed + counter + backend request concurrently
        text.textContent = "RUNNING SIMULATION";
        _simStartTelemetry();
        _simStartCounter();
        
        let maxOvers = 20;
        if (matchFormat === 'odi') maxOvers = 50;
        if (matchFormat === 'test') maxOvers = 450;

        const payload = {
            venueId: 1, 
            venueName: window.currentMatchVenue || 'Unknown Venue',
            battingTeamId: 1,
            bowlingTeamId: 2,
            battingTeamName: window.currentBattingTeam || 'T1',
            bowlingTeamName: window.currentBowlingTeam || 'T2',
            matchFormat: matchFormat,
            matchStatus: window.currentMatchStatus || 'live',
            currentRuns: parseInt(document.getElementById('currentRuns').value) || 0,
            currentWickets: parseInt(document.getElementById('currentWickets').value) || 0,
            overs: oversInput,
            targetScore: parseInt(document.getElementById('targetScore').value) || 0,
            maxOvers: maxOvers
        };

        const abortController = new AbortController();
        const timeoutId = setTimeout(() => abortController.abort(), 50000); // 50s timeout

        const fetchPromise = fetch(`${API_BASE_URL}/analyze`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: abortController.signal
        }).then(async res => {
            if (!res.ok) throw new Error("Backend offline");
            return res.json();
        }).catch(e => {
            console.warn("Fetch failed or timed out", e);
            return {
                winProbability: 0,
                projectedScore: 0,
                expectedRunsRemaining: 0,
                requiredRunRate: 0,
                momentumMeter: 0
            };
        });

        const aiPromise = fetch(`${API_BASE_URL}/intelligence`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: abortController.signal
        }).then(async res => {
            clearTimeout(timeoutId);
            if (!res.ok) throw new Error("AI Backend offline");
            return res.json();
        }).catch(e => {
            console.warn("AI Fetch failed or timed out", e);
            return {
                aiCommentary: ["AI intelligence temporarily unavailable."],
                venueIntelligence: null
            };
        });

        // Safety timeout: force-hide overlay after 55s to prevent permanent hang
        _loadingSafetyTimeout = setTimeout(() => {
            console.warn('[PitchIQ] Safety timeout — forcing overlay hide.');
            hideLoadingSequence();
        }, 55000);

        // Orchestrate backend work to happen DURING the existing animation window
        // Wait for both required result sets before ending the analysis sequence
        const [data, aiData] = await Promise.all([fetchPromise, aiPromise]);

        // --- Backend responded — begin completion sequence ---
        _simStopTelemetry();
        _simStopCounter();
        if (_loadingSafetyTimeout) { clearTimeout(_loadingSafetyTimeout); _loadingSafetyTimeout = null; }

        // Show completion feed line
        _simAddFeedLine("✓ Simulation converged", 'sim-feed-success');
        if (aiData.aiCommentary && aiData.aiCommentary.length > 0 && aiData.aiCommentary[0] !== "AI intelligence temporarily unavailable.") {
            _simAddFeedLine("✓ AI match intelligence generated", 'sim-feed-success');
        }
        text.textContent = "ANALYSIS COMPLETE";

        // Stop scanner, show final counter state
        if (scannerGlow) scannerGlow.classList.add('sim-scanner-stopped');
        fill.style.width = "100%";

        // Swap counter for done checkmark
        if (counterRow) counterRow.style.display = 'none';
        counterDone.style.display = "block";
        await new Promise(r => setTimeout(r, 400));

        // Fade out overlay
        overlay.style.opacity = '0';
        await new Promise(r => setTimeout(r, 500));

        // --- End Telemetry Simulation UX ---

        // Show HUD
        const hud = document.getElementById('hud');
        hud.style.display = 'flex';

        // Remove old stagger classes to reset animation
        const cards = hud.querySelectorAll('.glass-panel');
        cards.forEach(c => {
            c.classList.remove('stagger-enter');
            c.classList.add('stagger-in');
        });

        // Trigger staggered entrance
        setTimeout(() => {
            cards.forEach((c, i) => {
                setTimeout(() => {
                    c.classList.add('stagger-enter');
                }, i * 100);
            });
        }, 100); // small delay to ensure DOM updates

        // Prepare data
        let probPct = 0;
        if (!(payload.targetScore === 0 && payload.overs === 0 && payload.currentRuns === 0)) {
            probPct = Math.round(data.winProbability * 100);
        }
        
        let oversStr = payload.overs.toString();
        let oversParts = oversStr.split('.');
        let completedOvers = parseInt(oversParts[0]) || 0;
        let balls = oversParts.length > 1 ? parseInt(oversParts[1]) || 0 : 0;
        if (isNaN(completedOvers)) completedOvers = 0;
        if (isNaN(balls)) balls = 0;
        let oversDecimal = completedOvers + (balls / 6.0);
        let crr = 0;
        if (oversDecimal > 0 && payload.currentRuns > 0) {
            crr = payload.currentRuns / oversDecimal;
        }
        let rrr = data.requiredRunRate || 0;

        // Populate Top Match Header
        document.getElementById('headerTeams').textContent = `${payload.battingTeamName} vs ${payload.bowlingTeamName}`;
        document.getElementById('headerFormat').textContent = payload.matchFormat.toUpperCase();
        document.getElementById('headerVenue').textContent = payload.venueName;
        document.getElementById('headerScore').textContent = `${payload.currentRuns}/${payload.currentWickets} (${payload.overs})`;
        document.getElementById('headerTarget').textContent = payload.targetScore > 0 ? payload.targetScore.toString() : "-";
        
        let confidence = "Low";
        if (data.winProbability > 0.8 || data.winProbability < 0.2) confidence = "High";
        else if (data.winProbability > 0.6 || data.winProbability < 0.4) confidence = "Medium";
        document.getElementById('headerConfidence').textContent = confidence;
        
        let statusText = `<svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-2px;"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="4" fill="currentColor"></circle></svg> LIVE`;
        if (payload.matchStatus === 'upcoming') statusText = `<svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-2px;"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg> UPCOMING`;
        if (payload.matchStatus === 'completed' || payload.matchStatus === 'recent') statusText = `<svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-2px;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg> COMPLETED`;
        document.getElementById('headerMatchStatus').innerHTML = statusText;

        // Trigger Animated Count-ups
        animateValue("winProbRing", 0, probPct, 1200, false, true); 
        animateValue("projScoreText", 0, data.projectedScore, 1000, false, false);
        animateValue("crrText", 0, crr, 1000, true, false);
        animateValue("rrrText", 0, rrr, 1000, true, false);
        
        document.getElementById('expRunsText').textContent = data.expectedRunsRemaining;
        if (payload.targetScore === 0 && payload.overs === 0 && payload.currentRuns === 0) {
             document.getElementById('winProbText').textContent = `N/A`;
             document.getElementById('winProbRing').setAttribute('stroke-dasharray', `0, 100`);
        }

        // Populate Venue Report immediately with final AI data
        const vr = aiData.venueIntelligence;
        if (vr) {
            document.getElementById('venueReportPanel').style.display = 'block';
            document.getElementById('vrGround').textContent = vr.groundName || payload.venueName;
            document.getElementById('vrCity').textContent = vr.city || 'Unavailable';
            document.getElementById('vrPitchType').textContent = vr.pitchType || 'Unavailable';
            document.getElementById('vrAvg1st').textContent = vr.averageFirstInningsScore || 'Unavailable';
            document.getElementById('vrBatting').textContent = vr.battingRating || 'Unavailable';
            document.getElementById('vrBowling').textContent = vr.bowlingRating || 'Unavailable';
            document.getElementById('vrPace').textContent = vr.paceSupport || 'Unavailable';
            document.getElementById('vrSpin').textContent = vr.spinSupport || 'Unavailable';
            document.getElementById('vrToss').textContent = vr.tossAdvantage || 'Unavailable';
            document.getElementById('vrDew').textContent = vr.dewFactor || 'Unavailable';
            document.getElementById('vrVerdict').textContent = vr.recommendedStrategy || vr.shortSummary || 'Unavailable';
        } else {
            document.getElementById('venueReportPanel').style.display = 'none';
        }

        // Update PitchIQ Intelligence immediately with final AI data
        if (aiData.aiCommentary && Array.isArray(aiData.aiCommentary)) {
            const list = document.getElementById('intelligenceList');
            list.innerHTML = '';
            const icons = [
                '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>', 
                '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>', 
                '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>', 
                '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle></svg>', 
                '<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M9.59 4.59A2 2 0 1 1 11 8H2m10.59 11.41A2 2 0 1 0 14 16H2m15.73-8.27A2.5 2.5 0 1 1 19.5 12H2"></path><circle cx="12" cy="12" r="3"></circle></svg>' 
            ];
            for (let i = 0; i < Math.min(5, aiData.aiCommentary.length); i++) {
                const li = document.createElement('li');
                li.innerHTML = `<span class="intelligence-icon" style="display:inline-flex; align-items:center; justify-content:center; margin-right:8px; color:var(--accent);">${icons[i] || '•'}</span> <span>${aiData.aiCommentary[i]}</span>`;
                list.appendChild(li);
            }

            if (aiData.aiCommentary.length > 1 && aiData.aiCommentary[1] !== "AI intelligence temporarily unavailable.") {
                const insightDiv = document.getElementById('preMatchInsight');
                insightDiv.style.display = 'block';
                insightDiv.innerHTML = `<strong>Venue Insight:</strong> ${aiData.aiCommentary[1]}`;
            } else {
                document.getElementById('preMatchInsight').style.display = 'none';
            }
            logEvent(analytics, 'generate_intelligence');
        }

    } catch (e) {
        console.error("Simulation failed", e);
    } finally {
        hideLoadingSequence();
        btn.disabled = false;
    }
    }); // End authGuard
});

// Helper to detect terminal match statuses
function isTerminalStatus(status) {
    if (!status || typeof status !== 'string') return false;
    const s = status.toLowerCase();
    return (s.includes('won by') && !s.includes('toss won by')) ||
           s.includes('lost by') ||
           s.includes('draw') ||
           s.includes('drawn') ||
           s.includes('tie') ||
           s.includes('tied') ||
           s.includes('no result') ||
           s.includes('abandoned') ||
           s.includes('cancelled') ||
           s.includes('canceled') ||
           s.includes('awarded') ||
           s.includes('match ended') ||
           s.includes('refused to play') ||
           s.includes('conceded') ||
           s.includes('walkover') ||
           s.includes('concluded') ||
           s.includes('postponed');
}

// Fetch Live Matches on Load and every 60 seconds
async function fetchLiveMatches() {
    try {
        const response = await fetch(`${API_BASE_URL}/matches/live`);
        if (!response.ok) throw new Error('Live matches unavailable');
        const matches = await response.json();
        
        const liveContainer = document.getElementById('liveMatchesContainer');
        const recentContainer = document.getElementById('recentMatchesContainer');
        const upcomingContainer = document.getElementById('upcomingMatchesContainer');
        
        if (!matches || matches.length === 0) {
            if (liveContainer.childElementCount === 0) {
                liveContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No live matches available. Switched to Manual Mode.</div>';
                if (!manualModeToggle.checked) manualModeToggle.click();
            }
            return;
        }

        // Only clear once we know we have data
        liveContainer.innerHTML = '';
        recentContainer.innerHTML = '';
        upcomingContainer.innerHTML = '';

        let liveCount = 0, recentCount = 0, upcomingCount = 0;

        const now = new Date();

        matches.forEach(match => {
            const card = document.createElement('div');
            card.className = 'match-card';
            
            const isTerminal = isTerminalStatus(match.status);
            const isStumps = match.status && (match.status.toLowerCase().includes('stump') || match.status.toLowerCase().includes('day '));

            let latestScore = '';
            let overs = 0;
            let wickets = 0;
            let runs = 0;
            let targetScore = 0;
            if (match.scores && match.scores.length > 0) {
                const s = match.scores[match.scores.length - 1]; // Use the latest inning
                latestScore = `${s.runs}/${s.wickets} (${s.overs} ov)`;
                runs = s.runs;
                wickets = s.wickets;
                overs = s.overs;
                if (match.scores.length > 1) {
                    targetScore = match.scores[0].runs + 1;
                }
            } else if (match.matchEnded || isTerminal) {
                latestScore = match.status || "Match Ended";
            } else if (match.matchStarted) {
                // Live match with no score data — show status context, not "starting soon"
                latestScore = match.status || "Live";
            } else {
                latestScore = "Match starting soon";
            }

            // Use official API short names or backend-generated abbreviations
            const t1 = match.battingTeamShort || (match.battingTeam ? match.battingTeam.substring(0, 3).toUpperCase() : "T1");
            const t2 = match.bowlingTeamShort || (match.bowlingTeam ? match.bowlingTeam.substring(0, 3).toUpperCase() : "T2");
            
            let section = 'live'; // 'live', 'recent', 'upcoming', 'skip'
            let statusBadge = `<svg viewBox="0 0 24 24" width="10" height="10" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-1px;"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="4" fill="currentColor"></circle></svg> LIVE`;

            if (match.matchEnded || isTerminal) {
                // Terminal / completed results must NEVER be displayed as LIVE
                section = 'recent';
                statusBadge = `<svg viewBox="0 0 24 24" width="10" height="10" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-1px;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg> COMPLETED`;
            } else if (match.matchStarted || isStumps) {
                section = 'live';
                statusBadge = `<svg viewBox="0 0 24 24" width="10" height="10" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-1px;"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="4" fill="currentColor"></circle></svg> LIVE`;
            } else {
                // Upcoming: Only show if scheduled start time is genuinely in the future
                if (match.dateTimeGMT) {
                    try {
                        let gmtStr = match.dateTimeGMT.endsWith('Z') ? match.dateTimeGMT : match.dateTimeGMT + 'Z';
                        const dt = new Date(gmtStr);
                        if (dt <= now) {
                            // Scheduled start time has passed and match has not started
                            return;
                        }
                    } catch (e) {}
                }
                section = 'upcoming';
                statusBadge = `<svg viewBox="0 0 24 24" width="10" height="10" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:2px; vertical-align:-1px;"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg> UPCOMING`;
            }
            
            if (section === 'skip') return;

            let displayStatus = match.status;
            
            if (displayStatus.startsWith('Match starts at ') && match.dateTimeGMT) {
                try {
                    let gmtStr = match.dateTimeGMT.endsWith('Z') ? match.dateTimeGMT : match.dateTimeGMT + 'Z';
                    const dt = new Date(gmtStr);
                    const now = new Date();
                    
                    // Force IST (Asia/Kolkata) to ensure uniform time display for users globally
                    const options = { 
                        timeZone: 'Asia/Kolkata', 
                        hour: 'numeric', 
                        minute: '2-digit', 
                        hour12: true, 
                        timeZoneName: 'short' 
                    };
                    // 'en-IN' ensures correct AM/PM and formatting conventions
                    const timeStr = dt.toLocaleTimeString('en-IN', options).toUpperCase();
                    
                    // Format date (Today, Tomorrow, or Month Day) based on IST date
                    // We need to compare dates in IST to avoid edge cases near midnight
                    const istOptionsDate = { timeZone: 'Asia/Kolkata', year: 'numeric', month: 'numeric', day: 'numeric' };
                    const dtIstStr = dt.toLocaleDateString('en-IN', istOptionsDate);
                    const nowIstStr = now.toLocaleDateString('en-IN', istOptionsDate);
                    
                    let dateStr = "";
                    if (dtIstStr === nowIstStr) {
                        dateStr = "Today";
                    } else {
                        const tomorrow = new Date(now);
                        tomorrow.setDate(now.getDate() + 1);
                        if (dtIstStr === tomorrow.toLocaleDateString('en-IN', istOptionsDate)) {
                            dateStr = "Tomorrow";
                        } else {
                            dateStr = dt.toLocaleDateString('en-IN', { timeZone: 'Asia/Kolkata', month: 'short', day: 'numeric' });
                        }
                    }
                    
                    displayStatus = `Starts ${dateStr}, ${timeStr}`;
                } catch (e) {
                    console.error("Date format error", e);
                }
            } else if (displayStatus.includes(" GMT")) {
                // Fallback for live/recent matches that might have GMT in their status
                const timeMatch = displayStatus.match(/(\d{1,2}):(\d{2}) GMT/);
                if (timeMatch) {
                    let h = parseInt(timeMatch[1]);
                    let m = parseInt(timeMatch[2]);
                    const dt = new Date();
                    dt.setUTCHours(h, m, 0, 0);
                    displayStatus = displayStatus.replace(timeMatch[0], dt.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit', hour12: true }));
                }
            }

            // Hide bottom status line if it's identical to the score line (avoids duplicate text)
            const showStatus = latestScore !== displayStatus;

            card.innerHTML = `
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
                    <span class="match-status">${statusBadge}</span>
                    <span style="font-size:10px;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:1px;font-weight:600;">${match.matchType || 'Match'}</span>
                </div>
                <div class="match-title">${t1} vs ${t2}</div>
                <div class="match-score" style="color:var(--text-primary);font-weight:600;font-size:14px;margin:6px 0;">${latestScore}</div>
                <div style="font-size:10px;color:var(--text-tertiary);">${match.venue || 'Unknown Venue'}</div>
                ${showStatus ? `<div style="font-size:10px;color:var(--accent-warm);margin-top:6px;font-weight:500;">${displayStatus}</div>` : ''}
            `;
            
            card.onclick = async () => {
                // Clear any existing error toasts
                document.querySelectorAll('.match-error-toast').forEach(e => e.remove());

                // Remove active and loading classes from all match cards
                document.querySelectorAll('.match-card').forEach(c => {
                    c.classList.remove('active');
                    c.classList.remove('loading');
                });
                
                // Add active and loading class to clicked card
                card.classList.add('active');
                card.classList.add('loading');

                // Show full screen overlay instantly for immediate feedback
                showLoadingSequence();
                const text = document.getElementById('simStageText');
                if (text) text.textContent = "LOADING MATCH DATA";

                // Ensure manual mode is off when a live match is clicked
                if (manualModeToggle.checked) {
                    manualModeToggle.click();
                }
                
                try {
                    const detailResponse = await fetch(`${API_BASE_URL}/matches/${match.id}`);
                    if (!detailResponse.ok) {
                        throw new Error('Match details not found');
                    }
                    
                    const detailedMatch = await detailResponse.json();
                    if (detailedMatch.scores && detailedMatch.scores.length > 0) {
                        const ds = detailedMatch.scores[detailedMatch.scores.length - 1];
                        runs = ds.runs;
                        wickets = ds.wickets;
                        overs = ds.overs;
                    }
                } catch(e) {
                    console.warn('[PitchIQ] Could not fetch match details.', e);
                    card.classList.remove('loading');
                    hideLoadingSequence();
                    
                    // Show clear error state to user instead of silently failing
                    const errorToast = document.createElement('div');
                    errorToast.className = 'match-error-toast';
                    errorToast.textContent = 'Failed to load match details. Please try again.';
                    card.after(errorToast);
                    
                    // Remove toast after a few seconds
                    setTimeout(() => {
                        errorToast.style.opacity = '0';
                        setTimeout(() => errorToast.remove(), 400);
                    }, 4000);
                    return; // Stop execution on error
                }
                
                // Remove loading state from card (overlay stays as simulation starts)
                card.classList.remove('loading');

                // Store venue context for simulations & AI
                window.currentMatchVenue = match.venue || 'Unknown Venue';
                window.currentBattingTeam = match.battingTeam || t1;
                window.currentBowlingTeam = match.bowlingTeam || t2;
                window.currentMatchStatus = section;

                // Auto-populate
                document.getElementById('currentRuns').value = runs;
                document.getElementById('currentWickets').value = wickets;
                document.getElementById('oversBowled').value = overs;
                if (targetScore > 0) {
                    document.getElementById('targetScore').value = targetScore;
                } else {
                    document.getElementById('targetScore').value = 0;
                }
                
                // Determine match format from matchType or match name
                let mType = match.matchType ? match.matchType.toLowerCase() : 't20';
                if (match.name && match.name.toLowerCase().includes('odi')) {
                    mType = 'odi';
                } else if (match.name && match.name.toLowerCase().includes('test')) {
                    mType = 'test';
                }
                
                document.getElementById('matchFormat').value = mType;

                const btn = document.getElementById('analyzeBtn');

                // Adjust button text for different modes
                if (!match.matchStarted) {
                    btn.innerHTML = 'PREVIEW FIXTURE <span class="arr">&rarr;</span>';
                } else {
                    btn.innerHTML = 'RUN SIMULATION <span class="arr">&rarr;</span>';
                }
                
                window.currentMatchId = match.id;
                logEvent(analytics, 'select_live_match');

                // Automatically trigger the full analysis flow
                // This programmatically clicks the analyze button which
                // invokes authGuard -> simulation -> HUD rendering
                btn.click();
            };
            
            if (section === 'live') {
                liveContainer.appendChild(card);
                liveCount++;
            } else if (section === 'recent') {
                if (recentCount < 12) {
                    recentContainer.appendChild(card);
                }
                recentCount++;
            } else if (section === 'upcoming') {
                upcomingContainer.appendChild(card);
                upcomingCount++;
            }
        });
        
        if (liveCount === 0) liveContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No live matches currently in progress.</div>';
        if (recentCount === 0) recentContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No recent matches available.</div>';
        if (upcomingCount === 0) upcomingContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No upcoming fixtures.</div>';
        
    } catch (e) {
        console.warn("Failed to fetch live matches silently.", e);
        const liveContainer = document.getElementById('liveMatchesContainer');
        // Only show error if container is completely empty
        if (liveContainer.childElementCount === 0) {
            liveContainer.innerHTML = '<div style="color: var(--accent-warm); font-size: 13px;">Unable to load live matches. Switched to Manual Mode.</div>';
            if (!manualModeToggle.checked) manualModeToggle.click();
        }
    }
}

// Initial fetch
fetchLiveMatches();
// Refresh every 60 seconds
setInterval(() => {
    if (document.visibilityState === 'visible') {
        fetchLiveMatches();
    }
}, 60000);

window.activeAnimations = window.activeAnimations || {};

function animateValue(id, start, end, duration, isFloat = false, isRing = false) {
    const obj = document.getElementById(id);
    if (!obj) return;
    
    // Cancel any existing animation for this specific element to prevent layout thrashing
    if (window.activeAnimations[id]) {
        window.cancelAnimationFrame(window.activeAnimations[id]);
    }
    
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        // easeOutQuart
        const ease = 1 - Math.pow(1 - progress, 4);
        const current = ease * (end - start) + start;
        
        if (isRing) {
            // Update the SVG stroke-dasharray and text content
            obj.setAttribute('stroke-dasharray', `${Math.round(current)}, 100`);
            const textObj = document.getElementById('winProbText');
            if (textObj) textObj.textContent = `${Math.round(current)}%`;
        } else if (isFloat) {
            obj.innerHTML = current.toFixed(2);
        } else {
            obj.innerHTML = Math.floor(current);
        }
        
        if (progress < 1) {
            window.activeAnimations[id] = window.requestAnimationFrame(step);
        } else {
            delete window.activeAnimations[id];
        }
    };
    window.activeAnimations[id] = window.requestAnimationFrame(step);
}

// Ask PI Implementation
let askPiHistory = [];

const askPiInput = document.getElementById('askPiInput');
const askPiSendBtn = document.getElementById('askPiSendBtn');
const askPiChat = document.getElementById('askPiChat');

function getMatchContext() {
    // Gather all visible telemetry
    const winProb = document.getElementById('winProbText').innerText;
    const rrr = document.getElementById('rrrText').innerText;
    const crr = document.getElementById('crrText').innerText;
    const projScore = document.getElementById('projScoreText').innerText;
    
    let intelligence = [];
    document.querySelectorAll('#intelligenceList li').forEach(li => {
        intelligence.push(li.innerText);
    });

    const activeMatch = document.querySelector('.match-card.active');
    let matchTitle = 'Unknown Match';
    if (activeMatch) {
        matchTitle = activeMatch.querySelector('.match-title').innerText;
    }

    return `Match: ${matchTitle}
Venue: ${window.currentMatchVenue || 'Unknown Venue'}
Win Probability: ${winProb}
Required Run Rate: ${rrr}
Current Run Rate: ${crr}
Projected Score: ${projScore}
PitchIQ Intelligence Bullets:
${intelligence.join('\n')}`;
}

async function sendAskPiMessage() {
    const question = askPiInput.value.trim();
    if (!question) return;

    authGuard(async () => {
        logEvent(analytics, 'ask_pi_query');
        // Append user message
        appendMessage(question, 'user-message');
        askPiInput.value = '';
        askPiInput.disabled = true;
        askPiSendBtn.disabled = true;

    // Append typing indicator
    const typingId = 'typing-' + Date.now();
    appendMessage('...', 'pi-message typing', typingId);

    try {
        const payload = {
            question: question,
            matchContext: getMatchContext(),
            history: askPiHistory
        };

        const response = await fetch(`${API_BASE_URL}/ask`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        
        // Remove typing indicator
        const typingEl = document.getElementById(typingId);
        if (typingEl) typingEl.remove();

        const answer = data.answer || "I'm having trouble processing that right now.";
        appendMessage(answer, 'pi-message');

        // Update history (keep last 3 messages to save tokens)
        askPiHistory.push({ role: 'user', content: question });
        askPiHistory.push({ role: 'assistant', content: answer });
        if (askPiHistory.length > 6) {
            askPiHistory = askPiHistory.slice(askPiHistory.length - 6);
        }

    } catch (error) {
        const typingEl = document.getElementById(typingId);
        if (typingEl) typingEl.remove();
        appendMessage("Sorry, I could not connect to the intelligence engine.", 'pi-message');
    } finally {
        askPiInput.disabled = false;
        askPiSendBtn.disabled = false;
        askPiInput.focus();
    }
    }); // End authGuard
}

function appendMessage(text, className, id = null) {
    const div = document.createElement('div');
    div.className = `chat-message ${className}`;
    if (id) div.id = id;
    // Basic markdown bold support
    div.innerHTML = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    askPiChat.appendChild(div);
    askPiChat.scrollTop = askPiChat.scrollHeight;
}

askPiSendBtn.addEventListener('click', sendAskPiMessage);
askPiInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') sendAskPiMessage();
});

// Footer Intersection Observer for fade-in animation
const footer = document.getElementById('premiumFooter');
if (footer) {
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.1 });
    observer.observe(footer);
}
