import { useState, useRef, useEffect } from "react";

function Navbar() {
    const [searchOpen, setSearchOpen] = useState(false);
    const [searchText, setSearchText] = useState("");
    const [profileOpen, setProfileOpen] = useState(false);

    const searchInputRef = useRef(null);

    // Get logged-in username
    const username =
        document.getElementById("react-navbar")?.dataset.username || "";

    // Get first letter
    const initial = username
        ? username.charAt(0).toUpperCase()
        : "U";

    // Focus search input when opened
    useEffect(() => {
        if (searchOpen && searchInputRef.current) {
            searchInputRef.current.focus();
        }
    }, [searchOpen]);

    // Search when Enter is pressed
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
        <nav className="navbar">

            {/* Logo */}
            <div className="logo">
                <img src="/images/logo.png" alt="PrepStart Logo" />
            </div>

            {/* Navigation Links */}
            <div className="nav-links">
                <a href="/home">Home</a>
                <a href="/dashboard">Dashboard</a>
                <a href="/modules">My Modules</a>
                <a href="/upload">Upload Content</a>
                <a href="/quizme">QuizMe</a>
                <a href="/progress">Progress</a>
            </div>

            {/* Search + Profile */}
            <div className="nav-actions">

                {/* Search */}
                <div
                    className={`search-box ${
                        searchOpen ? "expanded" : ""
                    }`}
                >
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
                        onClick={() =>
                            setSearchOpen(!searchOpen)
                        }
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

                {/* Profile */}
                <div className="profile-menu">

                    <button
                        type="button"
                        className="profile-dropdown-button"
                        onClick={() =>
                            setProfileOpen(!profileOpen)
                        }
                    >
                        <span className="profile-circle">
                            {initial}
                        </span>

                        <span className="dropdown-arrow"></span>
                    </button>

                    {/* Dropdown */}
                    {profileOpen && (
                        <div className="profile-dropdown">

                            <a href="/profile">
                                Profile
                            </a>

                            <a href="/settings">
                                Settings
                            </a>

                            <a href="/logout">
                                Logout
                            </a>

                        </div>
                    )}

                </div>

            </div>

        </nav>
    );
}

export default Navbar;