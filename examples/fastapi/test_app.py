from fastapi import FastAPI

app = FastAPI(title="Test API", version="1.0.0")

@app.get("/")
async def root():
    return {"message": "Hello from Vardr Secure FastAPI!"}

@app.get("/health")
async def health():
    return {"status": "healthy"}

@app.get("/info")
async def info():
    return {
        "framework": "FastAPI",
        "version": "1.0.0",
        "secure": True,
        "distroless": False,
        "python": "3.13"
    } 