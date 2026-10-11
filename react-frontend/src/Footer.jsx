function Footer() {
    return (
        <footer className="footer">

            {/* Upper Footer */}
            <div className="footer-content">

                {/* Brand */}
                <div className="footer-brand">
                    <img src="/images/logo.png" alt="PrepStart Logo" />
                    <p>By Students, For Students.</p>
                </div>

                {/* Quick Links */}
                <div className="footer-links">
                    <h3>Quick Links</h3>
                    <a href="/home">Home</a>
                    <a href="/modules">My Modules</a>
                    <a href="/upload">Upload Content</a>
                    <a href="/quiz">QuizMe</a>
                </div>

                {/* Support */}
                <div className="footer-links">
                    <h3>Support</h3>
                    <a href="/help">Help Centre</a>
                    <a href="/contact">Contact Us</a>
                    <a href="/feedback">Feedback</a>
                </div>

                {/* Contact */}
                <div className="footer-contact">
                    <h3>Contact Us</h3>
                    <p>+27 (0)11 347 2812</p>
                </div>

            </div>

            {/* Copyright */}
            <div className="footer-bottom">
                <p>Copyright © 2026 PrepStart. All rights reserved.</p>
            </div>

        </footer>
    );
}

export default Footer;

