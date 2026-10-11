
import { useState, useRef, useEffect } from "react";

function LandingNavbar() {
    const [searchOpen, setSearchOpen] = useState(false);
    const [searchText, setSearchText] = useState("");

    const searchInputRef = useRef(null);

    useEffect(() => {
        if (searchOpen && searchInputRef.current) {
            searchInputRef.current.focus();
        }
    }, [searchOpen]);

    const handleSearch = (event) => {
        if (event.key === "Enter") {
            const search = searchText.trim();

            if (search !== "") {
                window.location.href =
                    "/search?query=" + encodeURIComponent(search);
            }
        }
    };

    return (
        <nav className="navbar landing-navbar">

            <div className="logo">
                <img src="/images/logo.png" alt="PrepStart Logo" />
            </div>

            <div className="nav-actions">

                {/* Search */}
                <div className={`search-box ${searchOpen ? "expanded" : ""}`}>

                    <input
                        ref={searchInputRef}
                        type="text"
                        className="search-input"
                        placeholder="Search..."
                        value={searchText}
                        onChange={(event) =>
                            setSearchText(event.target.value)
                        }
                        onKeyDown={handleSearch}
                    />

                    <button
                        type="button"
                        className="search-button"
                        aria-label="Search"
                        onClick={() => setSearchOpen(!searchOpen)}
                    >
                        <svg
                            width="20"
                            height="20"
                            viewBox="0 0 24 24"
                            fill="none"
                            xmlns="http://www.w3.org/2000/svg"
                        >
                            <circle
                                cx="11"
                                cy="11"
                                r="7"
                                stroke="currentColor"
                                strokeWidth="2"
                            />

                            <line
                                x1="16.65"
                                y1="16.65"
                                x2="21"
                                y2="21"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                            />
                        </svg>
                    </button>

                </div>

                {/* Login */}
                <a href="/login" className="login-button">
                    Login
                </a>

            </div>

        </nav>
    );
}

export default LandingNavbar;
