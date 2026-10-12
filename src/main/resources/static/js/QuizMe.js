// Lightweight UI only. Questions, scores and correct answers stay on the server.
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.querySelector('.mobile-nav-toggle');
    const nav = document.querySelector('.main-nav');
    if (toggle && nav) {
        toggle.addEventListener('click', () => {
            const open = nav.classList.toggle('nav-open');
            toggle.setAttribute('aria-expanded', String(open));
        });
    }

    // Prevent a double click from submitting the same answer twice.
    for (const form of document.querySelectorAll('#quizSetupForm, #answerForm')) {
        form.addEventListener('submit', () => {
            const submitButtons = document.querySelectorAll(`button[form="${form.id}"], #${form.id} button[type="submit"]`);
            submitButtons.forEach(button => {
                button.classList.add('is-loading');
                // Don't disable submit buttons: the browser may cancel the form submit.
                button.style.pointerEvents = 'none';
            });
        });
    }
});
