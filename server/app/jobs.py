def start_scheduler():
    class _Noop:
        def shutdown(self, wait=False):
            pass
    return _Noop()
