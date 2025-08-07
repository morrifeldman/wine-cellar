#!/usr/bin/env python3
"""
Simple web server to run the interactive color picker
"""

import http.server
import socketserver
import webbrowser
import os
from pathlib import Path

def main():
    # Change to wine cellar directory
    os.chdir("/home/morri/wine-cellar")
    
    PORT = 8000
    
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory="/home/morri/wine-cellar", **kwargs)
    
    try:
        with socketserver.TCPServer(("", PORT), Handler) as httpd:
            print(f"Server running at http://localhost:{PORT}")
            print(f"Opening web color picker...")
            print(f"Navigate to: http://localhost:{PORT}/tools/wine-color-picker/web_color_picker.html")
            
            # Try to open browser automatically
            try:
                webbrowser.open(f"http://localhost:{PORT}/tools/wine-color-picker/web_color_picker.html")
            except:
                print("Could not open browser automatically")
            
            print("\nPress Ctrl+C to stop server")
            httpd.serve_forever()
            
    except KeyboardInterrupt:
        print("\nServer stopped")
    except OSError as e:
        if "Address already in use" in str(e):
            print(f"Port {PORT} is already in use. Try a different port or kill the existing process.")
        else:
            print(f"Error starting server: {e}")

if __name__ == "__main__":
    main()